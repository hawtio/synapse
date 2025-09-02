# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# Use bash explicitly in this Makefile to avoid unexpected platform
# incompatibilities among Linux distros.
#
SHELL := /bin/bash

MAKE := make --no-print-directory

#
# Allows for resources to be loaded from outside the root location of
# the kustomize config file. Ensures that resource don't need to be
# copied around the file system.
#
# See https://kubectl.docs.kubernetes.io/faq/kustomize
#
KOPTIONS := --load-restrictor LoadRestrictionsNone

#
# Include the main camel-k Makefile containing
# basic common recipes like kustomize and vars
# like VERSION
#
include script/Makefile

#
# Vars that can be overridden by external env vars
#
DRY_RUN ?= false
NAMESPACE ?= janus-mcp

# Cluster on which to install [ openshift | k8s ]
CLUSTER_TYPE ?= k8s
# Use ssl for internal server communication [ true | false ]
INTERNAL_SERVER_SSL ?= true
# The name of the service-account user
JANUS_USER ?= janus-user

# Uninstall all resources: [true|false]
UNINSTALL_ALL ?=false

BASE := base
PATCHES := patches
PLACEHOLDER := placeholder

SSL_DEPLOYMENT_SECRET_PATCH := $(PATCHES)/patch-deployment-ssl-serving-secret.yml
SSL_DEPLOYMENT_PORTS_PATCH := $(PATCHES)/patch-deployment-ssl-ports.yml
SSL_SERVICE_PATCH := $(PATCHES)/patch-service-ssl-ports.yml
SSL_INGRESS_PATCH := patch-ingress-ssl-ports.yml

#
# Macro for editing kustomization to define
# the image reference
#
# Parameter: directory of the kustomization.yaml
#
define set-kustomize-image
	$(if $(filter $(IMAGE_FULL_NAME),$(CUSTOM_IMAGE_FULL_NAME):$(CUSTOM_VERSION)),,\
		@cd $(1) || exit 1 && \
			$(KUSTOMIZE) edit set image $(IMAGE_FULL_NAME)=$(CUSTOM_IMAGE_FULL_NAME):$(CUSTOM_VERSION))
endef

#
# Macro for editing kustomization to define
# the namespace
#
# Parameter: directory of the kustomization.yaml
#
define set-kustomize-namespace
	@cd $(1) || exit 1 && \
		$(KUSTOMIZE) edit set namespace $(NAMESPACE)
endef

#
# Add or remove a patch on a kustomization.yaml
# targetting a kind of resource
#
# Parameters:
# * directory of the kustomization.yaml
# * [add, remove]
# * path of patch
# * kind of resources, eg. Deployment, Role
#
define add-remove-kind-patch
	@cd $(1) || exit 1 && \
		$(KUSTOMIZE) edit $(2) patch --path $(3) --kind $(4) &> /dev/null
endef

#
# Checks if the cluster user has the necessary privileges to be a cluster-admin
# In this case if the user can list the CRDs then probably a cluster-admin
#
check-admin: kubectl
	@output=$$(kubectl get crd 2>&1) || (echo "****" && echo "**** ERROR: Cannot continue as user is not a Cluster-Admin ****" && echo "****"; exit 1)

# SSL support is mandatory for OpenShift so no need to add
.ssl-support:
ifneq ($(CLUSTER_TYPE), openshift)
ifeq ($(INTERNAL_SERVER_SSL), true)
	# SSL optional for other kubernetes platforms
	@$(call add-remove-kind-patch,$(BASE),add,../$(SSL_DEPLOYMENT_SECRET_PATCH),Deployment)
	@$(call add-remove-kind-patch,$(BASE),add,../$(SSL_DEPLOYMENT_PORTS_PATCH),Deployment)
	@$(call add-remove-kind-patch,$(BASE),add,../$(SSL_SERVICE_PATCH),Service)
	@$(call add-remove-kind-patch,$(CLUSTER_TYPE),add,../$(SSL_INGRESS_PATCH),Ingress)
else
	@$(call add-remove-kind-patch,$(BASE),remove,../$(SSL_DEPLOYMENT_SECRET_PATCH),Deployment)
	@$(call add-remove-kind-patch,$(BASE),remove,../$(SSL_DEPLOYMENT_PORTS_PATCH),Deployment)
	@$(call add-remove-kind-patch,$(BASE),remove,../$(SSL_SERVICE_PATCH),Service)
	@$(call add-remove-kind-patch,$(CLUSTER_TYPE),remove,../$(SSL_INGRESS_PATCH),Ingress)
endif
endif

#---
#
#@ install
#
#== Install the deployment into the cluster
#
#=== Calls kustomize
#=== Calls kubectl
#
#* PARAMETERS:
#** CLUSTER_TYPE:            Set the cluster type to install on
#**                            [ openshift | k8s ]
#** NAMESPACE:               Set the namespace for the resources
#** INTERNAL_SERVER_SSL:     Set whether SSL should be used for internal server communication
#**                            (only configurable in 'k8s' -- assumed in 'openshift')
#** CUSTOM_IMAGE_REGISTRY    Set a custom image registry to install from
#** CUSTOM_IMAGE_GROUP       Set a custom image group to install from
#** CUSTOM_IMAGE_NAME        Set a custom image name to install from
#** CUSTOM_VERSION:          Set a custom version to install from
#** DRY_RUN:                 Print the resources to be applied instead of applying them
#**                            [ true | false ]
#
#---
install: kustomize kubectl
# Set the namespace in the setup kustomization yaml
	@$(call set-kustomize-namespace, $(CLUSTER_TYPE))
# Set the image reference of the kustomization
	@$(call set-kustomize-image,$(CLUSTER_TYPE))
# Add the tls proxying on openshift, if specified
ifeq ($(CLUSTER_TYPE), openshift)
	@$(MAKE) -s generate-proxying
endif
# Add the tls serving on k8s, if specified
ifeq ($(CLUSTER_TYPE), k8s)
	@$(MAKE) -s generate-serving
endif
# Add ssl support in deployment
	@$(MAKE) -s .ssl-support
#
# Build the resources
# Either apply to the cluster or output to CLI
# Post-processes any remaining 'placeholder'
# that may remain, eg. in rolebindings
#
ifeq ($(DRY_RUN), false)
	@$(KUSTOMIZE) build $(KOPTIONS) $(CLUSTER_TYPE) | \
		sed 's/$(PLACEHOLDER)/$(NAMESPACE)/' | \
		kubectl apply -f -
else
	@$(KUSTOMIZE) build $(KOPTIONS) $(CLUSTER_TYPE) | \
		sed 's/$(PLACEHOLDER)/$(NAMESPACE)/'
endif

#---
#
#@ generate-proxying
#
#== Generate a client certificate and TLS secret for Hawtio proxying on Openshift
#
#=== Calls check-admin
#
#* PARAMETERS:
#** CLUSTER_TYPE:            Set the cluster type to install on
#**                            [ openshift | k8s ]
#**                            (must be openshift not applicable to kubernetes)
#** NAMESPACE:               Set the namespace for the secret
#
#---
generate-proxying: check-admin
ifneq ($(CLUSTER_TYPE), openshift)
	$(error "Only applicable to cluster-type of Openshift")
endif
	NAMESPACE=$(NAMESPACE) ./script/generate-proxying.sh

#---
#
#@ generate-serving
#
#== Generate a client certificate and TLS secret for Hawtio on kubernetes
#
#=== Calls check-admin
#
#* PARAMETERS:
#** CLUSTER_TYPE:            Set the cluster type to install on
#**                            [ openshift | k8s ]
#**                            (must be kubernetes not applicable to openshift)
#** NAMESPACE:               Set the namespace for the secret
#
#---
generate-serving: check-admin
ifneq ($(CLUSTER_TYPE), k8s)
	$(error "Only applicable to cluster-type of k8s")
endif
	NAMESPACE=$(NAMESPACE) ./script/generate-serving.sh


#---
#
#@ user
#
#== Generate a service-account user that can provide a bearer token for form-based access
#
#* PARAMETERS:
#** NAMESPACE:               Set the namespace for the secret
#** JANUS_USER:             The name of the service-account user
#
#---
user:
	NAMESPACE=$(NAMESPACE) JANUS_USER=$(JANUS_USER) ./script/create-user.sh

#---
#
#@ uninstall
#
#== Uninstall the resources previously installed.
#
#=== Cluster-admin privileges are required.
#
#* PARAMETERS:
#** CLUSTER_TYPE:            Set the cluster type to install on
#**                            [ openshift | k8s ]
#** NAMESPACE:               Set the namespace to uninstall the resources from
#** UNINSTALL_ALL:           Uninstall all resources including crds and cluster
#**                          roles installed by setup-cluster
#**                            [ true | false ]
#** DRY_RUN:                 Print the resources to be applied instead of applying them
#**                            [ true | false ]
#
#---
uninstall: kubectl kustomize
# Set the namespace in the all target kustomization yaml
	@$(call set-kustomize-namespace, $(CLUSTER_TYPE))
ifeq ($(DRY_RUN), false)
	@$(KUSTOMIZE) build $(KOPTIONS) $(CLUSTER_TYPE) | kubectl delete --ignore-not-found=true -f -
else
	@$(KUSTOMIZE) build $(KOPTIONS) $(CLUSTER_TYPE) | kubectl delete --dry-run=client -f -
endif

.DEFAULT_GOAL := help
.PHONY: check_admin .openshift-console-url .hawtconfig .ssl_support install generate-proxying generate-serving cluster-auth-client user uninstall help

help: ## Show this help screen.
	@awk 'BEGIN { printf "\nUsage: make \033[31m<PARAM1=val1 PARAM2=val2>\033[0m \033[36m<target>\033[0m\n"; printf "\nAvailable targets are:\n" } /^#@/ { printf "\033[36m%-15s\033[0m", $$2; subdesc=0; next } /^#===/ { printf "%-14s \033[32m%s\033[0m\n", " ", substr($$0, 5); subdesc=1; next } /^#==/ { printf "\033[0m%s\033[0m\n\n", substr($$0, 4); next } /^#\*\*/ { printf "%-14s \033[31m%s\033[0m\n", " ", substr($$0, 4); next } /^#\*/ && (subdesc == 1) { printf "\n"; next } /^#\-\-\-/ { printf "\n"; next }' $(MAKEFILE_LIST)
