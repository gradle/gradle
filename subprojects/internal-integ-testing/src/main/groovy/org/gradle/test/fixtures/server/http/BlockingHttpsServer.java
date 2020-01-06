/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.test.fixtures.server.http;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.gradle.test.fixtures.keystore.TestKeyStore;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyStore;

/**
 * An HTTPS server fixture for tests.
 *
 * Unless you really need HTTPS, you should use a plain {@link BlockingHttpServer}.
 */
public class BlockingHttpsServer extends BlockingHttpServer {
    public BlockingHttpsServer() throws IOException {
        super(HttpsServer.create(new InetSocketAddress(0), 10), 120000, Scheme.HTTPS);
    }

    public void configure(TestKeyStore testKeyStore) {
        HttpsServer httpsServer = (HttpsServer) this.server;
        SSLContext context = createSSLContext(testKeyStore);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(context) {
            @Override
            public void configure(HttpsParameters params) {
                SSLContext c = getSSLContext();
                SSLEngine engine = c.createSSLEngine();
                params.setNeedClientAuth(false);
                params.setCipherSuites(engine.getEnabledCipherSuites());
                params.setProtocols(engine.getEnabledProtocols());
                params.setSSLParameters(c.getDefaultSSLParameters());
            }
        });
    }

    // Create the and initialize the SSLContext
    private SSLContext createSSLContext(TestKeyStore testKeyStore) {
        try {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            char[] keyStorePassword = testKeyStore.getKeyStorePassword().toCharArray();
            keyStore.load(new FileInputStream(testKeyStore.getKeyStore()), keyStorePassword);

            // Create key manager
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
            keyManagerFactory.init(keyStore, keyStorePassword);
            KeyManager[] km = keyManagerFactory.getKeyManagers();

            // Create trust manager
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
            trustManagerFactory.init(keyStore);
            TrustManager[] tm = trustManagerFactory.getTrustManagers();

            // Initialize SSLContext
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(km, tm, null);
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
