#!/usr/bin/env bash

mkdir /root/.kube

echo $K8S_KUBECTL_CONFIG_BASE64 | base64 -d > /root/.kube/config
helm init --client-only

export NAMESPACE=cibuild${DRONE_REPO/stm-labs\//-}-${DRONE_BUILD_NUMBER}
echo "Undeploy all from k8s namespace ${NAMESPACE}..."

helm ls | cut -f1 | tail -n +2 | grep $NAMESPACE | xargs helm delete --purge

exit 0
