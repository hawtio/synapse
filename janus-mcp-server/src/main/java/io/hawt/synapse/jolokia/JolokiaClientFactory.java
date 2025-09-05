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

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.management.MalformedObjectNameException;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.Header;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jolokia.client.J4pAuthenticator;
import org.jolokia.client.J4pClient;
import org.jolokia.client.J4pClientBuilder;
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

    private static final Logger LOG = Logger.getLogger(JolokiaClientFactory.class);

    @Override
    @CacheResult(cacheName = "jolokia-clients")
    public JolokiaService createService(String url, String authToken) throws Exception {
        return new JolokiaClient(url, authToken);
    }
    
    private class BearerTokenAuthenticator implements J4pAuthenticator {
        private final String token;

        BearerTokenAuthenticator(String token) {
            this.token = token;
        }

        @Override
        public void authenticate(HttpClientBuilder pBuilder,String pUser, String pPassword) {
            Header authHeader = new BasicHeader("Authorization", "Bearer " + this.token);

            /*
             * Set it as a default header on the HttpClientBuilder.
             * This ensures it's added to every request made by the client.
             */
            pBuilder.setDefaultHeaders(Collections.singletonList(authHeader));
        }
    }

    private class JolokiaClient implements JolokiaService {

        private static final String CLIENT_CERT_PATH = "/etc/tls/private/proxying/tls.crt";
        private static final String CLIENT_KEY_PATH = "/etc/tls/private/proxying/tls.key";

        private J4pClient jolokiaClient;
        private MBeanListCache mbeanListCache;

        public JolokiaClient(
                @ConfigProperty(name = "jolokia.mcp.url", defaultValue = "http://localhost:8778/jolokia") String jolokiaUrl, String authToken) throws Exception {
            LOG.debugf("Creating the Jolokia client [ url: %s, authToken: %s ]", jolokiaUrl, authToken);

            SSLContext sslContext = this.createPermissiveTLSContext();
            // Do not verify the hostname
            LOG.debug("Creating socket factory with no-op hostname verifier");
            SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                    sslContext, NoopHostnameVerifier.INSTANCE);

            LOG.debug("Constructing J4pClient with SSL and Bearer Token support");
            jolokiaClient = new J4pClient(jolokiaUrl);
            jolokiaClient = new J4pClientBuilder()
                    .url(jolokiaUrl)
                    .sslConnectionSocketFactory(socketFactory)
                    .authenticator(new BearerTokenAuthenticator(authToken))
                    .build();
        }

        /**
         * SSL Context that is permissive to jolokia target application certificates
         * as these may be self-signed or not signed by the OCP CA.
         * Presents the proxy certificate/key to the target application to ensure SSL
         * is setup as part of the comms protocol.
         *
         * @return SSLContext
         */
        private SSLContext createPermissiveTLSContext() throws Exception {
            LOG.debugf("Creating the permissive SSL context [ certificate: %s. key: %s ]", CLIENT_CERT_PATH, CLIENT_KEY_PATH);

            // Load the client's full certificate chain from the PEM file
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> certs;
            try (InputStream certInput = new FileInputStream(CLIENT_CERT_PATH)) {
                certs = certFactory.generateCertificates(certInput);
            }

            // Load the client's private key from the PEM file
            byte[] keyBytes = Files.readAllBytes(Paths.get(CLIENT_KEY_PATH));
            String keyPEM = new String(keyBytes)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replaceAll("\\R", "")
                .replace("-----END PRIVATE KEY-----", "");
            byte[] decodedKey = Base64.getDecoder().decode(keyPEM);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedKey);
            PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(spec);

            // Create an in-memory KeyStore
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            char[] password = "".toCharArray();
            keyStore.load(null, password);
            keyStore.setKeyEntry("client-cert-key", privateKey, password, certs.toArray(new Certificate[0]));

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, password);
            KeyManager[] keyManagers = kmf.getKeyManagers();

            // Create the permissive "trust-all" TrustManager ---
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            // Combine them into the final SSLContext ---
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustAllCerts, new java.security.SecureRandom());
            return sslContext;
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
