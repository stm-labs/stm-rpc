#!/usr/bin/env bash

COLOR_OFF='\033[0m'

function out {
  COLOR='\033[0;35m'

  echo -e "${COLOR}[CI]: $1${COLOR_OFF}"
}

set -e

HOME_DIR=$(cd `dirname $0` && pwd)


MVN_PARAMS='-Psign-artifacts'
mvnp() {
    mvn ${MVN_PARAMS} "$@"
}
export -f mvnp

function deploy() {
    out "Deploying $1"
    cd $HOME_DIR/$1 && mvnp deploy
}

out 'Clean and Install all modules'
cd $HOME_DIR && mvnp clean install

deploy rpc-dependencies
deploy core
deploy rpc-kafka-redis
deploy rpc-local-handler
deploy rpc-router



