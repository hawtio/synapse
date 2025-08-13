/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.hawt.synapse.jolokia;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.management.MalformedObjectNameException;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jolokia.client.J4pClient;
import org.jolokia.client.exception.J4pException;
import org.jolokia.client.request.J4pExecRequest;
import org.jolokia.client.request.J4pExecResponse;
import org.jolokia.client.request.J4pListRequest;
import org.jolokia.client.request.J4pListResponse;
import org.jolokia.client.request.J4pReadRequest;
import org.jolokia.client.request.J4pReadResponse;
import org.jolokia.client.request.J4pRequest;
import org.jolokia.client.request.J4pVersionRequest;
import org.jolokia.client.request.J4pVersionResponse;
import org.jolokia.client.request.J4pWriteRequest;
import org.jolokia.client.request.J4pWriteResponse;
import org.jolokia.json.JSONObject;

import io.hawt.synapse.janus.JolokiaService;
import io.hawt.synapse.janus.JolokiaServiceFactory;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped // The Factory IS a CDI bean.
public class JolokiaClientFactory implements JolokiaServiceFactory {

    @Override
    @CacheResult(cacheName = "jolokia-clients")
    public JolokiaService createService(String url) {
        return new JolokiaClient(url);
    }

    private class JolokiaClient implements JolokiaService {

        private J4pClient jolokiaClient;
        private MBeanListCache mbeanListCache;

        public JolokiaClient(
                @ConfigProperty(name = "jolokia.mcp.url", defaultValue = "http://localhost:8778/jolokia") String jolokiaUrl) {
            jolokiaClient = new J4pClient(jolokiaUrl);
        }

        @Override
        public List<String> listMBeans() throws J4pException {
            if (mbeanListCache != null && mbeanListCache.isValid()) {
                return mbeanListCache.getMBeans();
            }

            JSONObject domains = list(null);
            List<String> result = domains.entrySet().stream().flatMap(domain -> ((JSONObject) domain.getValue())
                    .keySet().stream().map(props -> domain.getKey() + ":" + props)).collect(Collectors.toList());
            mbeanListCache = new MBeanListCache(result);
            return result;
        }

        @Override
        public JSONObject listOperations(String mbean) throws J4pException {
            return getFromMBean(mbean, "op");
        }

        @Override
        public JSONObject listAttributes(String mbean) throws J4pException {
            return getFromMBean(mbean, "attr");
        }

        JSONObject getFromMBean(String mbean, String key) throws J4pException {
            JSONObject mbeanInfo = list(toPath(mbean));
            return (JSONObject) mbeanInfo.getOrDefault(key, new JSONObject());
        }

        static String toPath(String mbean) {
            return J4pRequest.escape(mbean).replaceFirst(":", "/");
        }

        JSONObject list(String path) throws J4pException {
            J4pListRequest req = new J4pListRequest(path);
            J4pListResponse resp = jolokiaClient.execute(req);
            return resp.getValue();
        }

        @Override
        public Optional<JSONObject> version() throws J4pException {
            J4pVersionRequest req = new J4pVersionRequest();
            J4pVersionResponse resp = jolokiaClient.execute(req);
            return Optional.ofNullable(resp.asJSONObject());
        }

        @Override
        public Optional<Object> read(String mbean, String attr) throws J4pException, MalformedObjectNameException {
            J4pReadRequest req = new J4pReadRequest(mbean, attr);
            J4pReadResponse resp = jolokiaClient.execute(req);
            return Optional.ofNullable(resp.getValue());
        }

        @Override
        public Optional<Object> write(String mbean, String attr, Object value)
                throws J4pException, MalformedObjectNameException {
            J4pWriteRequest req = new J4pWriteRequest(mbean, attr, value);
            J4pWriteResponse resp = jolokiaClient.execute(req);
            return Optional.ofNullable(resp.getValue());
        }

        @Override
        public Optional<Object> exec(String mbean, String op, Object... args)
                throws J4pException, MalformedObjectNameException {
            J4pExecRequest req = new J4pExecRequest(mbean, op, args);
            J4pExecResponse resp = jolokiaClient.execute(req);
            return Optional.ofNullable(resp.getValue());
        }
    }
}
