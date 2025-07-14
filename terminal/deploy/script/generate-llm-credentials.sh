#!/bin/bash

set -e -o pipefail

trap exithandler EXIT

TEMP_DIR=$(mktemp --tmpdir -d generate-proxying.XXXXXX)

exithandler() {
  exitcode=$?
  if [ "$exitcode" != "0" ]; then
    echo "WARNING: unsuccessful exit code: $exitcode" >&2
  fi

  rm -rf "$TEMP_DIR"

  exit $exitcode
}

usage() {
  cat <<EOT
This script generates a secret for the LLM credentials.

Usage:
  $(basename "$0") [-h]

Options:
  -h    Show this help
EOT
  exit
}

kube_binary() {
  local k
  k=$(command -v "${1}" 2> /dev/null)
  # shellcheck disable=SC2181
  if [ $? != 0 ]; then
    return
  fi

  echo "${k}"
}

while getopts :hk:n:p:u: OPT; do
  case $OPT in
    h)
      usage
      ;;
    k)
      LLM_API_KEY=${OPTARG}
      ;;
    n)
      LLM_NAME=${OPTARG}
      ;;
    p)
      LLM_PROVIDER=${OPTARG}
      ;;
    u)
      LLM_BASE_URL=${OPTARG}
      ;;
    *)
      ;;
  esac
done

if [ -n "${KUBECLI}" ]; then
  KUBECLI=$(kube_binary "${KUBECLI}")
else
  # try finding oc
  KUBECLI=$(kube_binary oc)
  if [ -z "${KUBECLI}" ]; then
    # try finding kubectl
    KUBECLI=$(kube_binary kubectl)
  fi
fi

if [ -z "${KUBECLI}" ]; then
  echo "Error: Cannot find kube cluster client command, eg. oc or kubectl"
  exit 1
fi

if [ -z "${NAMESPACE}" ]; then
  NAMESPACE=$(${KUBECLI} config view --minify -o jsonpath='{..namespace}')

  if [ -z "${NAMESPACE}" ]; then
    echo "Error: Cannot determine the target namespace for the new secret"
    exit 1
  fi
fi

if [ -z "${LLM_PROVIDER}" ]; then
  echo "Error: LLM Provider (-p) is required"
  exit 1
fi

SECRET_NAME=synapse-terminal-llm-credentials

if ${KUBECLI} get secret "${SECRET_NAME}" -n "${NAMESPACE}" 1> /dev/null 2>& 1; then
  echo "The secret ${SECRET_NAME} in ${NAMESPACE} already exists"
  exit 0
fi

# Create the secret for llm credentials
${KUBECLI} create secret generic "${SECRET_NAME}" \
  --from-literal=provider=${LLM_PROVIDER} \
  --from-literal=key=${LLM_API_KEY} \
  --from-literal=name=${LLM_NAME} \
  --from-literal=url=${LLM_BASE_URL} \
  -n "${NAMESPACE}"
