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

VERSION := 0.0.1
LAST_RELEASED_IMAGE_NAME := synapse-janus
LAST_RELEASED_VERSION ?= 0.0.0
CONTROLLER_GEN_VERSION := v0.6.1
KUSTOMIZE_VERSION := v4.5.4
OPM_VERSION := v1.24.0

IMAGE_REGISTRY := quay.io
IMAGE_GROUP := hawtio
IMAGE_NAME := synapse-janus

# Replace SNAPSHOT with the current timestamp
DATETIMESTAMP=$(shell date -u '+%Y%m%d-%H%M%S')
VERSION := $(subst -SNAPSHOT,-$(DATETIMESTAMP),$(VERSION))

#
# Situations when user wants to override
# the image name and version
# - used in kustomize install
# - need to preserve original image and version as used in other files
#
CUSTOM_IMAGE_REGISTRY ?= $(IMAGE_REGISTRY)
CUSTOM_IMAGE_GROUP ?= $(IMAGE_GROUP)
CUSTOM_IMAGE_NAME ?= $(IMAGE_NAME)
CUSTOM_VERSION ?= $(VERSION)
IMAGE_PUSH ?= false

RELEASE_GIT_REMOTE := origin
GIT_COMMIT := $(shell if [ -d .git ]; then git rev-list -1 HEAD; else echo "$(CUSTOM_VERSION)"; fi)
LINT_GOGC := 10
LINT_DEADLINE := 10m

define LICENSE_HEADER
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
endef

export LICENSE_HEADER
default: build

kubectl:
ifeq (, $(shell command -v kubectl 2> /dev/null))
	$(error "No kubectl found in PATH. Please install and re-run")
endif

kustomize:
ifeq (, $(shell command -v kustomize 2> /dev/null))
	$(error "No kustomize found in PATH. Please install and re-run")
else
KUSTOMIZE=$(shell command -v kustomize 2> /dev/null)
endif

npx:
ifeq (, $(shell command -v npx 2> /dev/null))
	$(error "No npx found in PATH. Please install and re-run")
else
NPX=$(shell command -v npx 2> /dev/null)
endif

mvn:
ifeq (, $(shell command -v mvn 2> /dev/null))
	$(error "No mvn found in PATH. Please install and re-run")
else
NPX=$(shell command -v mvn 2> /dev/null)
endif

container-builder:
ifeq (, $(shell command -v podman 2> /dev/null))
ifeq (, $(shell command -v docker 2> /dev/null))
	$(error "No podman or docker found in PATH. Please install and re-run")
else
CONTAINER_BUILDER=$(shell command -v docker 2> /dev/null)
endif
else
CONTAINER_BUILDER=$(shell command -v podman 2> /dev/null)
endif

build: mvn
	@echo "####### Building janus-mcp-server ..."
	mvn clean package \
	  -D skipTests \
	  -D quarkus.container-image.registry=$(CUSTOM_IMAGE_REGISTRY) \
	  -D quarkus.container-image.group=$(CUSTOM_IMAGE_GROUP) \
	  -D quarkus.container-image.name=$(CUSTOM_IMAGE_NAME) \
	  -D quarkus.container-image.tag=$(CUSTOM_VERSION) \
	  -D quarkus.container-image.push=$(IMAGE_PUSH)

test-inspect: npx
	npx @modelcontextprotocol/inspector

clean:
	mvn clean -D skipTests

.PHONY: kubectl kustomize yarn setup terminal-build build clean image-terminal
