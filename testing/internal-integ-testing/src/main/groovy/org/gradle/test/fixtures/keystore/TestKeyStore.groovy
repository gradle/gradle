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
import java.security.Key
import java.security.KeyStore
import java.security.cert.Certificate

@CompileStatic
class TestKeyStore {
    TestFile trustStore
    String trustStorePassword = "asdfgh"
    TestFile keyStore
    String keyStorePassword = "asdfgh"
    String keyStoreType
    String trustStoreType

    static TestKeyStore init(TestFile rootDir, String keyStoreType = "PKCS12", String trustStoreType = keyStoreType) {
        new TestKeyStore(rootDir, keyStoreType, trustStoreType)
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

    private TestKeyStore(TestFile rootDir, String keyStoreType = "PKCS12", String trustStoreType = keyStoreType) {
        this.keyStoreType = keyStoreType
        this.trustStoreType = trustStoreType
        rootDir.mkdirs()
        keyStore = rootDir.file("clientStore")
        trustStore = rootDir.file("serverStore")

        copyCertFile("test-key-store/keyStore", keyStore, keyStoreType, keyStorePassword)
        copyCertFile("test-key-store/trustStore-adoptopenjdk-8.bin", trustStore, trustStoreType, trustStorePassword)
    }

    private static void copyCertFile(String path, TestFile clientStore, String type, String password) {
        URL fileUrl = ClassLoader.getSystemResource(path);
        KeyStore original = KeyStore.getInstance("JKS");
        original.load(fileUrl.openStream(), password.toCharArray());

        KeyStore target = KeyStore.getInstance(type);
        target.load(null, null);
        for (alias in original.aliases()) {
            if (original.isKeyEntry(alias)) {
                Key key = original.getKey(alias, password.toCharArray())
                target.setKeyEntry(alias, key, password.toCharArray(), original.getCertificateChain(alias));
            } else {
                Certificate cert = original.getCertificate(alias);
                target.setCertificateEntry(alias, cert);
            }
        }
        OutputStream os = clientStore.newOutputStream();
        target.store(os, password.toCharArray());
        os.close()
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
        def args = getTrustStoreArguments()
        if (GradleContextualExecuter.embedded) {
            args.each {
                executer.withArgument(it)
            }
        } else {
            executer.withBuildJvmOpts(args)
        }
    }

    void configureIncorrectServerCert(GradleExecuter executer) {
        // intentionally use wrong trust store
        def args = getTrustStoreSettings()
            .tap { it["javax.net.ssl.trustStore"] = keyStore.path }
            .collect { "-D${it.key}=${it.value}".toString() }
        if (GradleContextualExecuter.embedded) {
            args.each {
                executer.withArgument(it)
            }
        } else {
            executer.withBuildJvmOpts(args)
        }
    }

    void configureServerAndClientCerts(GradleExecuter executer) {
        def args = getServerAndClientCertArgs()
        if (GradleContextualExecuter.embedded) {
            args.each {
                executer.withArgument(it)
            }
        } else {
            executer.withBuildJvmOpts(args)
        }
    }

    Map<String, String> getTrustStoreSettings() {
        [
            "javax.net.ssl.trustStore": trustStore.path,
            "javax.net.ssl.trustStorePassword": trustStorePassword,
            "javax.net.ssl.trustStoreType": trustStoreType,
        ]
    }

    Map<String, String> getKeyStoreSettings() {
        [
            "javax.net.ssl.keyStore": keyStore.path,
            "javax.net.ssl.keyStorePassword": keyStorePassword,
            "javax.net.ssl.keyStoreType": keyStoreType,
        ]
    }

    Map<String, String> getServerAndClientCertSettings() {
        getTrustStoreSettings() + getKeyStoreSettings()
    }

    List<String> getTrustStoreArguments() {
        getTrustStoreSettings().collect { "-D${it.key}=${it.value}".toString() }
    }

    List<String> getKeyStoreArguments() {
        getKeyStoreSettings().collect { "-D${it.key}=${it.value}".toString() }
    }

    List<String> getServerAndClientCertArgs() {
        getServerAndClientCertSettings().collect { "-D${it.key}=${it.value}".toString() }
    }

    SSLContext asServerSSLContext() {
        return createServerSSLContext(this)
    }

    /**
     * Create the and initialize the SSLContext
     */
    private static SSLContext createServerSSLContext(TestKeyStore testKeyStore) {
        try {
            // Create key manager
            KeyStore keyStore = KeyStore.getInstance(testKeyStore.getTrustStoreType());
            char[] keyStorePassword = testKeyStore.getTrustStorePassword().toCharArray();

            testKeyStore.getTrustStore().withInputStream {keyStoreIn ->
                keyStore.load(keyStoreIn, keyStorePassword)
            }

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
            keyManagerFactory.init(keyStore, keyStorePassword);
            KeyManager[] km = keyManagerFactory.getKeyManagers();

            // Create trust manager
            KeyStore trustStore = KeyStore.getInstance(testKeyStore.getKeyStoreType());
            char[] trustStorePassword = testKeyStore.getKeyStorePassword().toCharArray();

            testKeyStore.getKeyStore().withInputStream {keyStoreIn ->
                trustStore.load(keyStoreIn, trustStorePassword)
            }

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
            trustManagerFactory.init(trustStore);
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
