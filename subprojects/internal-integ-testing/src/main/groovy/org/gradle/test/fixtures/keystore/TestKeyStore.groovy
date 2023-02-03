/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.test.fixtures.keystore

import groovy.transform.CompileStatic
import org.apache.commons.io.FileUtils
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.gradle.api.Action
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.Actions
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.HttpServerFixture

import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import java.security.KeyStore

@CompileStatic
class TestKeyStore {
    TestFile trustStore
    String trustStorePassword = "asdfgh"
    TestFile keyStore
    String keyStorePassword = "asdfgh"

    static TestKeyStore init(TestFile rootDir) {
        new TestKeyStore(rootDir)
    }

    /*
     clientStore/serverStore only contains self-signed certificates for embedded HTTPS server.
     To make the client work with both embedded HTTPS server and real-world HTTPS server (e.g. Maven Central),
     we need to merge JDK's cacerts into the custom truststore via:

     keytool -importkeystore -srckeystore <JDK cacerts file location> -destkeystore <resource>/test-key-store/trustStore

     Note:
      1. Use JDK8 keytool command to make sure compatibility.
      2. Default password for JDK cacerts is "changeit".

     The current trustStore-adoptopenjdk-8 is created from AdoptOpenJDK8 cacerts.
     */

    private TestKeyStore(TestFile rootDir) {
        keyStore = rootDir.file("clientStore")
        trustStore = rootDir.file("serverStore")

        copyCertFile("test-key-store/keyStore", keyStore)
        copyCertFile("test-key-store/trustStore-adoptopenjdk-8.bin", trustStore)
    }

    private static void copyCertFile(String s, TestFile clientStore) {
        URL fileUrl = ClassLoader.getSystemResource(s);
        FileUtils.copyURLToFile(fileUrl, clientStore);
    }

    void enableSslWithServerCert(
        HttpServerFixture server,
        Action<SslContextFactory.Server> configureServer = Actions.doNothing()
    ) {
        server.enableSsl(trustStore.path, trustStorePassword, null, null, configureServer)
    }

    void enableSslWithServerAndClientCerts(
        HttpServerFixture server,
        Action<SslContextFactory.Server> configureServer = Actions.doNothing()
    ) {
        server.enableSsl(trustStore.path, trustStorePassword, keyStore.path, keyStorePassword, configureServer)
    }

    void enableSslWithServerAndBadClientCert(
        HttpServerFixture server,
        Action<SslContextFactory.Server> configureServer = Actions.doNothing()
    ) {
        // intentionally use wrong trust store for server
        server.enableSsl(trustStore.path, trustStorePassword, trustStore.path, keyStorePassword, configureServer)
    }

    void configureServerCert(GradleExecuter executer) {
        if (GradleContextualExecuter.embedded) {
            executer.withArgument("-Djavax.net.ssl.trustStore=$trustStore.path")
            executer.withArgument("-Djavax.net.ssl.trustStorePassword=$trustStorePassword")
        } else {
            executer.withBuildJvmOpts("-Djavax.net.ssl.trustStore=$trustStore.path")
            executer.withBuildJvmOpts("-Djavax.net.ssl.trustStorePassword=$trustStorePassword")
        }
    }

    void configureIncorrectServerCert(GradleExecuter executer) {
        // intentionally use wrong trust store
        if (GradleContextualExecuter.embedded) {
            executer.withArgument("-Djavax.net.ssl.trustStore=$keyStore.path")
            executer.withArgument("-Djavax.net.ssl.trustStorePassword=$trustStorePassword")
        } else {
            executer.withBuildJvmOpts("-Djavax.net.ssl.trustStore=$keyStore.path")
            executer.withBuildJvmOpts("-Djavax.net.ssl.trustStorePassword=$trustStorePassword")
        }
    }

    void configureServerAndClientCerts(GradleExecuter executer) {
        if (GradleContextualExecuter.embedded) {
            executer.withArgument("-Djavax.net.ssl.trustStore=$trustStore.path")
                .withArgument("-Djavax.net.ssl.trustStorePassword=$trustStorePassword")
                .withArgument("-Djavax.net.ssl.keyStore=$keyStore.path")
                .withArgument("-Djavax.net.ssl.keyStorePassword=$keyStorePassword")
        } else {
            executer.withBuildJvmOpts("-Djavax.net.ssl.trustStore=$trustStore.path")
                .withBuildJvmOpts("-Djavax.net.ssl.trustStorePassword=$trustStorePassword")
                .withBuildJvmOpts("-Djavax.net.ssl.keyStore=$keyStore.path")
                .withBuildJvmOpts("-Djavax.net.ssl.keyStorePassword=$keyStorePassword")
        }
    }

    List<String> getServerAndClientCertArgs() {
        ["-Djavax.net.ssl.trustStore=$trustStore.path",
         "-Djavax.net.ssl.trustStorePassword=$trustStorePassword",
         "-Djavax.net.ssl.keyStore=$keyStore.path",
         "-Djavax.net.ssl.keyStorePassword=$keyStorePassword"
        ].collect { it.toString() }
    }

    SSLContext asSSLContext() {
        return createSSLContext(this)
    }

    /**
     * Create the and initialize the SSLContext
     */
    private static SSLContext createSSLContext(TestKeyStore testKeyStore) {
        try {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            char[] keyStorePassword = testKeyStore.getKeyStorePassword().toCharArray();

            testKeyStore.getKeyStore().withInputStream {keyStoreIn ->
                keyStore.load(keyStoreIn, keyStorePassword)
            }

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
