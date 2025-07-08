#!/bin/sh

# Fail on error and undefined vars
set -eu

export SYNAPSE_LOG_LEVEL="${SYNAPSE_LOG_LEVEL:-info}"

SYNAPSE_ENV_FILE="/opt/synapse/env.product"

if [ -f "${SYNAPSE_ENV_FILE}" ]; then
  cp ${SYNAPSE_ENV_FILE} /tmp
  sed -i -e "s/^LOG_LEVEL.*/LOG_LEVEL=${SYNAPSE_LOG_LEVEL}/" /tmp/env.product
  cat /tmp/env.product > ${SYNAPSE_ENV_FILE}
fi

node \
  --enable-source-maps \
  --env-file=${SYNAPSE_ENV_FILE} \
  /opt/synapse/synapse-host.js
