#!/bin/sh

# Fail on error and undefined vars
#set -eu

# Declare the variable with an already populated value if, otherwise null
export JWT_PUBLIC_KEY_LOCATION="${JWT_PUBLIC_KEY_LOCATION:-}"
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

echo Configuring public key for oauth validation ...

# This variable will hold the final path to the key
FINAL_PUBLIC_KEY_LOCATION=""

# --- Determine the public key location for oauth validation of the bearer token ---
if [ -n "${JWT_PUBLIC_KEY_LOCATION}" ]; then
  # Use the user-provided location if the env var is set
  echo "Using provided public key location: ${JWT_PUBLIC_KEY_LOCATION}"
  FINAL_PUBLIC_KEY_LOCATION="${JWT_PUBLIC_KEY_LOCATION}"
elif [ "${OPENSHIFT}" == "true" ]; then
  # Fall back to fetching the key from the OCP API
  OCP_PUBLIC_KEY_PATH="/tmp/jwks.json"
  echo "JWT_PUBLIC_KEY_LOCATION not set. Attempting to fetch OCP OAuth public key..."
  STATUS_CODE=$(curl --cacert ${CACERT} --header "Authorization: Bearer ${TOKEN}" -X GET "${APISERVER}"/openid/v1/jwks --write-out '%{http_code}' --silent --output "${OCP_PUBLIC_KEY_PATH}")
  echo "Return code from curl command ... ${STATUS_CODE}"
  if [ "${STATUS_CODE}" != "200" ]; then
    echo "ERROR: Failed to contact OCP OAuth Server"
    exit 1
  fi

  if [ $? -ne 0 ] || [ ! -s "${OCP_PUBLIC_KEY_PATH}" ]; then
    echo "ERROR: Failed to fetch OCP public key and JWT_PUBLIC_KEY_LOCATION is not set. Exiting."
    exit 1
  else
    echo "Public key fetched from OCP Cluster"
    FINAL_PUBLIC_KEY_LOCATION="${OCP_PUBLIC_KEY_PATH}"
  fi
else
  echo "ERROR: Cannot determine the JWT_PUBLIC_KEY_LOCATION env variable. Please specify this environment variable."
  exit 1
fi

KEY_LOCATION_PROPERTY=""
if [ -n "${FINAL_PUBLIC_KEY_LOCATION}" ]; then
  echo "Populating the publickey location"
  KEY_LOCATION_PROPERTY="-Dmp.jwt.verify.publickey.location=${FINAL_PUBLIC_KEY_LOCATION}"
fi

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
