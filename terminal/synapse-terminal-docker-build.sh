#!/bin/bash

pull_build_image() {
  local CONTAINER_IMAGE="${1:-}"
  local CONTAINER_VERSION="${2:-}"
  local CONTAINER_MAKE="${3:-}"

  podman pull ${CONTAINER_IMAGE}:${CONTAINER_VERSION}
  if [ "$?" == "0" ]; then
    echo "Skipping build because tag ${CONTAINER_VERSION} already exists"
  else
    make ${CONTAINER_MAKE}
    if [ "$?" != "0" ]; then
      echo "Error: make ${CONTAINER_MAKE} failed"
      exit 1
    fi

    podman push ${CONTAINER_IMAGE}:${CONTAINER_VERSION}
    if [ "$?" != "0" ]; then
      echo "Error: container push failed"
      exit 1
    fi
  fi
}


NAMESPACE=synapse
LLM_PROVIDER=google
LLM_API_KEY=AIzaSyC-AyPmsJLeX0J3n_BPUwG_rh-eN-kBc-E

MAKEFILE_VERSION=$(sed -n 's/^VERSION := \([0-9.]\+\)/\1/p' Makefile)
if [ -z "${MAKEFILE_VERSION}" ]; then
  echo "Error: Cannot extract Makefile version property"
  exit 1
fi

export CUSTOM_TERMINAL_IMAGE=quay.io/phantomjinx/hawtio-synapse-terminal
MY_DATE="$(date -u '+%Y%m%d%H%M')"

while getopts ":c:v:s" opt; do
  case ${opt} in
    c)
      CLUSTER_TYPE=${OPTARG}
      ;;
    s)
      ONLY_BUILD=1
      ;;
    v)
      CUSTOM_TERMINAL_VERSION=${OPTARG}
      ;;
    \?) echo "Usage: cmd [-s] [-v]"
      ;;
  esac
done

if [ -z "${CLUSTER_TYPE}" ]; then
  CLUSTER_TYPE=openshift
fi

if [ -z "${CUSTOM_TERMINAL_VERSION}" ]; then
  export CUSTOM_TERMINAL_VERSION="${MAKEFILE_VERSION}-${MY_DATE}"
else
  export CUSTOM_TERMINAL_VERSION="${CUSTOM_TERMINAL_VERSION}"
fi

# Try pulling or building existing image
pull_build_image "${CUSTOM_TERMINAL_IMAGE}" "${CUSTOM_TERMINAL_VERSION}" "image-terminal"

if [ "${ONLY_BUILD}" == "1" ]; then
  echo "Skipping install"
  exit 0
fi

pushd terminal/deploy &> /dev/null

CLUSTER_TYPE=${CLUSTER_TYPE} \
  NAMESPACE=${NAMESPACE} \
  CUSTOM_TERMINAL_IMAGE=${CUSTOM_TERMINAL_IMAGE} \
  CUSTOM_TERMINAL_VERSION=${CUSTOM_TERMINAL_VERSION} \
  LLM_PROVIDER=${LLM_PROVIDER} \
  LLM_API_KEY=${LLM_API_KEY} \
  make install

popd > /dev/null

oc get pods -w
