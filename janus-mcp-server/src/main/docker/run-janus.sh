#!/bin/sh

# Fail on error and undefined vars
#set -eu

# Declare the variable with an already populated value if, otherwise null
export APISERVER="https://${CLUSTER_MASTER:-kubernetes.default.svc}"
export SERVICEACCOUNT=/var/run/secrets/kubernetes.io/serviceaccount
export TOKEN=$(cat ${SERVICEACCOUNT}/token)
export CACERT=${SERVICEACCOUNT}/ca.crt
export OPENSHIFT=true

check_openshift_api() {
  STATUS_CODE=$(curl --cacert ${CACERT} --header "Authorization: Bearer ${TOKEN}" -X GET "${APISERVER}"/apis/apps.openshift.io/v1 --write-out '%{http_code}' --silent --output /dev/null)
  if [ "${STATUS_CODE}" != "200" ]; then
    OPENSHIFT=false
  fi
  echo "OpenShift API: ${OPENSHIFT} - ${STATUS_CODE} ${APISERVER}/apis/apps.openshift.io/v1"
}

check_openshift_api

# TODO Set this as an env var from the deployment
SSL_DEBUG="-Djavax.net.debug=ssl,handshake"

# --- Execute either JVM or Native mode ---
if [ -x "/opt/jboss/container/java/run/run-java.sh" ]; then
  # -- JVM MODE --
  echo "JVM mode detected. Using run-java.sh..."
  exec env JAVA_OPTS_APPEND="${SSL_DEBUG}" /opt/jboss/container/java/run/run-java.sh "$@"
else
  # -- NATIVE MODE --
  echo "Native mode detected. Executing application binary..."
  exec ./application ${SSL_DEBUG} "$@"
fi
