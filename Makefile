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
LAST_RELEASED_IMAGE_NAME := hawtio/synapse
LAST_RELEASED_VERSION ?= 0.0.0
CONTROLLER_GEN_VERSION := v0.6.1
OPERATOR_SDK_VERSION := v1.26.1
KUSTOMIZE_VERSION := v4.5.4
OPM_VERSION := v1.24.0
TERMINAL_IMAGE_NAME ?= quay.io/hawtio/synapse-terminal

# Replace SNAPSHOT with the current timestamp
DATETIMESTAMP=$(shell date -u '+%Y%m%d-%H%M%S')
VERSION := $(subst -SNAPSHOT,-$(DATETIMESTAMP),$(VERSION))


TERMINAL := terminal
TERMINAL_DOCKERFILE=$(TERMINAL)/deploy/Dockerfile-terminal

#
# Situations when user wants to override
# the image name and version
# - used in kustomize install
# - need to preserve original image and version as used in other files
#
CUSTOM_TERMINAL_IMAGE ?= $(TERMINAL_IMAGE_NAME)
CUSTOM_TERMINAL_VERSION ?= $(VERSION)

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

yarn:
ifeq (, $(shell command -v yarn 2> /dev/null))
	$(error "No yarn found in PATH. Please install and re-run")
else
YARN=$(shell command -v yarn 2> /dev/null)
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

setup: yarn
	cd $(TERMINAL) && yarn install

terminal-build: setup
	@echo "####### Building hawtio/synapse-terminal ..."
	cd $(TERMINAL) && yarn build

build: terminal-build

clean:
	rm -rf $(TERMINAL)/dist

image-terminal: container-builder
	@echo "####### Building Terminal container image..."
	$(CONTAINER_BUILDER) build -t $(CUSTOM_TERMINAL_IMAGE):$(CUSTOM_TERMINAL_VERSION) -f $(TERMINAL_DOCKERFILE) .

.PHONY: kubectl kustomize yarn setup terminal-build build clean image-terminal
