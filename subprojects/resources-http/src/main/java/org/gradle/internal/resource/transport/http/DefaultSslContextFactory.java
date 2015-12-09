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

package org.gradle.internal.resource.transport.http;

import org.apache.http.ssl.SSLInitializationException;
import org.gradle.internal.Factory;
import org.gradle.internal.SystemProperties;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/*
 * This implementation is borrowed from the Apache HttpClient project
 * https://github.com/apache/httpclient/blob/4.2.2/httpclient/src/main/java/org/apache/http/conn/ssl/SSLSocketFactory.java#L246-L354
 */
public class DefaultSslContextFactory implements Factory<SSLContext> {
    private static final char[] EMPTY_PASSWORD = "".toCharArray();

    @Override
    public SSLContext create() {
        try {
            TrustManagerFactory tmFactory;

            String trustAlgorithm = System.getProperty("ssl.TrustManagerFactory.algorithm");
            if (trustAlgorithm == null) {
                trustAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            }
            String trustStoreType = System.getProperty("javax.net.ssl.trustStoreType");
            if (trustStoreType == null) {
                trustStoreType = KeyStore.getDefaultType();
            }
            if ("none".equalsIgnoreCase(trustStoreType)) {
                tmFactory = TrustManagerFactory.getInstance(trustAlgorithm);
            } else {
                File trustStoreFile;
                String s = System.getProperty("javax.net.ssl.trustStore");
                if (s != null) {
                    trustStoreFile = new File(s);
                    tmFactory = TrustManagerFactory.getInstance(trustAlgorithm);
                    String trustStoreProvider = System.getProperty("javax.net.ssl.trustStoreProvider");
                    KeyStore trustStore;
                    if (trustStoreProvider != null) {
                        trustStore = KeyStore.getInstance(trustStoreType, trustStoreProvider);
                    } else {
                        trustStore = KeyStore.getInstance(trustStoreType);
                    }
                    String trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
                    FileInputStream instream = new FileInputStream(trustStoreFile);
                    try {
                        trustStore.load(instream, trustStorePassword != null ? trustStorePassword.toCharArray() : EMPTY_PASSWORD);
                    } finally {
                        instream.close();
                    }
                    tmFactory.init(trustStore);
                } else {
                    File javaHome = SystemProperties.getInstance().getJavaHomeDir();
                    File file = new File(javaHome, "lib/security/jssecacerts");
                    if (!file.exists()) {
                        file = new File(javaHome, "lib/security/cacerts");
                        trustStoreFile = file;
                    } else {
                        trustStoreFile = file;
                    }
                    tmFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                    String trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
                    FileInputStream instream = new FileInputStream(trustStoreFile);
                    try {
                        trustStore.load(instream, trustStorePassword != null ? trustStorePassword.toCharArray() : null);
                    } finally {
                        instream.close();
                    }
                    tmFactory.init(trustStore);
                }
            }

            KeyManagerFactory kmFactory = null;
            String keyAlgorithm = System.getProperty("ssl.KeyManagerFactory.algorithm");
            if (keyAlgorithm == null) {
                keyAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
            }
            String keyStoreType = System.getProperty("javax.net.ssl.keyStoreType");
            if (keyStoreType == null) {
                keyStoreType = KeyStore.getDefaultType();
            }
            if ("none".equalsIgnoreCase(keyStoreType)) {
                kmFactory = KeyManagerFactory.getInstance(keyAlgorithm);
            } else {
                File keyStoreFile = null;
                String s = System.getProperty("javax.net.ssl.keyStore");
                if (s != null) {
                    keyStoreFile = new File(s);
                }
                if (keyStoreFile != null) {
                    kmFactory = KeyManagerFactory.getInstance(keyAlgorithm);
                    String keyStoreProvider = System.getProperty("javax.net.ssl.keyStoreProvider");
                    KeyStore keyStore;
                    if (keyStoreProvider != null) {
                        keyStore = KeyStore.getInstance(keyStoreType, keyStoreProvider);
                    } else {
                        keyStore = KeyStore.getInstance(keyStoreType);
                    }
                    String keyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword");
                    FileInputStream instream = new FileInputStream(keyStoreFile);
                    try {
                        keyStore.load(instream, keyStorePassword != null ? keyStorePassword.toCharArray() : EMPTY_PASSWORD);
                    } finally {
                        instream.close();
                    }
                    kmFactory.init(keyStore, keyStorePassword != null ? keyStorePassword.toCharArray() : EMPTY_PASSWORD);
                }
            }

            SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(
                kmFactory != null ? kmFactory.getKeyManagers() : null,
                tmFactory != null ? tmFactory.getTrustManagers() : null,
                null);

            return sslcontext;
        } catch (GeneralSecurityException e) {
            throw new SSLInitializationException(e.getMessage(), e);
        } catch (IOException e) {
            throw new SSLInitializationException(e.getMessage(), e);
        }
    }
}
