#!/usr/bin/env bash

COLOR_OFF='\033[0m'

function out {
  COLOR='\033[0;35m'

  echo -e "${COLOR}[CI]: $1${COLOR_OFF}"
}

set -e

# идем вверх тк .drone.yml в папке devops
cd ../

HOME_FOLDER=`pwd`

MVN_PARAMS='-s /root/maven_settings.xml -B'
export NAMESPACE=cibuild${DRONE_REPO/stm-labs\//-}-${DRONE_BUILD_NUMBER}
export MAVEN_HOME=`pwd`/.m2
export M2_HOME_PATH=$MAVEN_HOME
export MVN_REPO_PATH=http://nexus.k8s-usn.stm.local/repository/maven-snapshots/

echo "use M2_HOME_PATH=${M2_HOME_PATH}"

mvnp() {
    /usr/bin/mvn ${MVN_PARAMS} "$@"
}
export -f mvnp

function getDockerRepository {
    mvnp org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=docker.image.prefix 2>/dev/null | grep -Ev '(^\[|Download\w+:)'
}

function getDockerArtifactName {
    mvnp org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=docker.image.name 2>/dev/null | grep -Ev '(^\[|Download\w+:)'
}

function getArtifactFinalName {
    mvnp org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.build.finalName 2>/dev/null | grep -Ev '(^\[|Download\w+:)'
}

out "Create namespace for build ${NAMESPACE}"

DEPLOY_START=`date +%s`

# for deploy dockers
mkdir /root/.docker
echo $DOCKER_AUTH_CONFIG > /root/.docker/config.json

# for helm & kubectl
mkdir /root/.kube
echo $K8S_KUBECTL_CONFIG_BASE64 | base64 -d > /root/.kube/config

helm init --client-only


export CHARTS_PATHS=helm-charts
git clone https://github.com/stm-labs/$CHARTS_PATHS.git

cd $CHARTS_PATHS

  if [ `git rev-parse --verify -q "origin/${DRONE_BRANCH}"` ]; then
    out "Checking out ${DRONE_BRANCH}"
    git checkout -f "origin/${DRONE_BRANCH}"
  else
    out "Checking out master"
    git checkout -f origin/master
  fi

cd ../

out "Deploy platform-components"

kubectl create namespace ${NAMESPACE}

# добавляем в rancher проект "CI Build" все что связано с автотестами
kubectl annotate namespace ${NAMESPACE} field.cattle.io/projectId=c-mgjq7:p-8zrvm

helm install --name build-${NAMESPACE} \
    --namespace ${NAMESPACE} \
    --set 'redis.usePassword=false' \
    --set 'redis.cluster.enabled=false' \
    --set 'redis.sentinel.enabled=false' \
    --set 'cp-schema-registry.enabled=false' \
    --set 'cp-kafka-connect.enabled=false' \
    --set 'cp-kafka-rest.enabled=false' \
    --set 'cp-ksql-server.enabled=false' \
    --set 'cp-control-center.enabled=false' \
    --set cp-helm-charts.cp-kafka.configurationOverrides."auto\.create\.topics\.enable"=false \
    --set cp-helm-charts.cp-kafka.configurationOverrides."offsets\.topic\.replication\.factor"=1 \
    --set cp-helm-charts.cp-kafka.configurationOverrides."auto\.create\.topics\.enable"=false \
    --set-string cp-helm-charts.cp-kafka.configurationOverrides."message\.max\.bytes"="10485760" \
    --set-string cp-helm-charts.cp-kafka.configurationOverrides."replica\.fetch\.max\.bytes"="10485760" \
    --set 'cp-helm-charts.cp-kafka.persistence.enabled=true' \
    --set 'cp-helm-charts.cp-kafka.customEnv.KAFKA_LOG4J_LOGGERS=kafka.controller=WARN,state.change.logger=WARN' \
    --set 'cp-helm-charts.cp-kafka.customEnv.KAFKA_LOG4J_ROOT_LOGLEVEL=WARN' \
    --set 'cp-helm-charts.cp-kafka.customEnv.KAFKA_TOOLS_LOG4J_LOGLEVEL=ERROR' \
    --set 'cp-helm-charts.cp-kafka.brokers=1' \
    --set 'cp-helm-charts.cp-zookeeper.servers=1' \
    --wait \
    --atomic \
    ./$CHARTS_PATHS/rpc-kafka-redis

out "Everything is Deployed"

DEPLOY_END=`date +%s`
DEPLOY_TIME=$((DEPLOY_START-DEPLOY_END))
printf 'Deployed environment in %dh:%dm:%ds\n' $((DEPLOY_TIME/3600)) $((DEPLOY_TIME%3600/60)) $((DEPLOY_TIME%60))

status=$?

if [[ "${status}" == 0 ]];then

  status=1

  cd /

  mvnp org.apache.maven.plugins:maven-dependency-plugin:3.1.1:purge-local-repository \
   -DmanualInclude=ru.stm-labs.rpc  \
   -DactTransitively=false  \
   -DreResolve=false

# TODO: load stm parent
#  mvnp org.apache.maven.plugins:maven-dependency-plugin:2.4:get \
#    -Dartifact=ru.stm-labs.rpc:rpc-dependencies:1.0.1-SNAPSHOT:pom \
#    -DremoteRepositories=usn-snapshots::::$MVN_REPO_PATH

  echo "Building project with maven"

  cd $HOME_FOLDER/rpc-dependencies && mvnp install
  cd $HOME_FOLDER/core && mvnp install
  cd $HOME_FOLDER/rpc-kafka-redis && mvnp install
  cd $HOME_FOLDER/rpc-router && mvnp install
  cd $HOME_FOLDER/rpc-local-handler && mvnp install

  KAFKA_TESTS=build-${NAMESPACE}-cp-kafka.${NAMESPACE}:9092

  #  skip integration tests if commit message contains [usn-skip-build-tests]
  if [[ $CI_COMMIT_MESSAGE != *"[stm-skip-build-tests]"* ]]; then
      cd $HOME_FOLDER

      echo "Run kafka redis RPC tests"
      mvnp test -f rpc-kafka-redis/pom.xml \
      -Dtest-profile=IntegrationTest \
      -Dusn.rpc.kafkaredis.namespace.inside.consumer.kafka.bootstrap-servers=${KAFKA_TESTS} \
      -Dusn.rpc.kafkaredis.namespace.inside.redis.host=build-${NAMESPACE}-master.${NAMESPACE} \
      -Dusn.rpc.kafkaredis.namespace.dmz.consumer.kafka.bootstrap-servers=${KAFKA_TESTS} \
      -Dusn.rpc.kafkaredis.namespace.dmz.redis.host=build-${NAMESPACE}-master.${NAMESPACE}

      else
        out "Ci integration tests were skipped !"
  fi

  if [[ "${DRONE_BRANCH}" == "master" ]];then
        # если мы на мастер то делаем deploy
        echo "On master branch - deploy  all RPC libs"
        cd $HOME_FOLDER/rpc-dependencies && mvnp deploy
        cd $HOME_FOLDER/core && mvnp deploy
        cd $HOME_FOLDER/rpc-kafka-redis && mvnp deploy
        cd $HOME_FOLDER/rpc-router && mvnp deploy
        cd $HOME_FOLDER/rpc-local-handler && mvnp deploy
  fi

  status=0
  echo -e "\n"

  RED='\033[0;31m'
  GREEN='\033[0;32m'

  if [[ "${status}" == 0 ]];then

    # below is only cleanup
    set +e

    echo -e "${GREEN}****************************"
    echo -e "${GREEN}[CI]: Success Build ${DRONE_BUILD_NUMBER}"
    echo -e "${GREEN}****************************"
  else
    echo -e "${RED}xxxxxxxxxxxxxxxxxxxxxx"
    echo -e "${RED}[CI]: Failed"
    echo -e "${RED}xxxxxxxxxxxxxxxxxxxxxx"
  fi

  echo -e "${COLOR_OFF}"
fi

exit $status
