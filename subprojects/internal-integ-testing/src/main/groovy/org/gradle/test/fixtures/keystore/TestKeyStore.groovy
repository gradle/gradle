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

import org.apache.commons.io.FileUtils
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.HttpServer

class TestKeyStore {
    TestFile trustStore
    String trustStorePassword = "asdfgh"
    TestFile keyStore
    String keyStorePassword = "asdfgh"

    static TestKeyStore init(TestFile rootDir) {
        new TestKeyStore(rootDir)
    }

    private TestKeyStore(TestFile rootDir) {
        keyStore = rootDir.file("clientStore")
        trustStore = rootDir.file("serverStore")

        copyCertFile("test-key-store/keyStore", keyStore)
        copyCertFile("test-key-store/trustStore", trustStore)
    }

    private static void copyCertFile(String s, TestFile clientStore) {
        URL fileUrl = ClassLoader.getSystemResource(s);
        FileUtils.copyURLToFile(fileUrl, clientStore);
    }

    void enableSslWithServerCert(HttpServer server) {
        server.enableSsl(trustStore.path, trustStorePassword)
    }

    void enableSslWithServerAndClientCerts(HttpServer server) {
        server.enableSsl(trustStore.path, trustStorePassword, keyStore.path, keyStorePassword)
    }

    void enableSslWithServerAndBadClientCert(HttpServer server) {
        // intentionally use wrong trust store for server
        server.enableSsl(trustStore.path, trustStorePassword, trustStore.path, keyStorePassword)
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
}
