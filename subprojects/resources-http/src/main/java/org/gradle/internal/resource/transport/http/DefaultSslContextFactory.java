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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import org.apache.http.ssl.SSLInitializationException;
import org.gradle.internal.SystemProperties;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class DefaultSslContextFactory implements SslContextFactory {
    private static final char[] EMPTY_PASSWORD = "".toCharArray();
    private static final Set<String> SSL_SYSTEM_PROPERTIES = ImmutableSet.of(
        "ssl.TrustManagerFactory.algorithm",
        "javax.net.ssl.trustStoreType",
        "javax.net.ssl.trustStore",
        "javax.net.ssl.trustStoreProvider",
        "javax.net.ssl.trustStorePassword",
        "ssl.KeyManagerFactory.algorithm",
        "javax.net.ssl.keyStoreType",
        "javax.net.ssl.keyStore",
        "javax.net.ssl.keyStoreProvider",
        "javax.net.ssl.keyStorePassword"
    );

    private LoadingCache<Map<String, String>, SSLContext> cache = CacheBuilder.newBuilder().softValues().build(new SslContextCacheLoader());

    @Override
    public SSLContext createSslContext() {
        return cache.getUnchecked(getCurrentProperties());
    }

    private Map<String, String> getCurrentProperties() {
        Map<String, String> currentProperties = new TreeMap<String, String>();
        for (String prop : SSL_SYSTEM_PROPERTIES) {
            currentProperties.put(prop, System.getProperty(prop));
        }
        currentProperties.put("java.home", SystemProperties.getInstance().getJavaHomeDir().getPath());
        return currentProperties;
    }

    private static class SslContextCacheLoader extends CacheLoader<Map<String, String>, SSLContext> {
        @Override
        public SSLContext load(Map<String, String> props) {
            // This implementation is borrowed from the Apache HttpClient project
            // https://github.com/apache/httpclient/blob/4.2.2/httpclient/src/main/java/org/apache/http/conn/ssl/SSLSocketFactory.java#L246-L354
            try {
                TrustManagerFactory tmFactory;

                String trustAlgorithm = props.get("ssl.TrustManagerFactory.algorithm");
                if (trustAlgorithm == null) {
                    trustAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
                }
                String trustStoreType = props.get("javax.net.ssl.trustStoreType");
                if (trustStoreType == null) {
                    trustStoreType = KeyStore.getDefaultType();
                }
                if ("none".equalsIgnoreCase(trustStoreType)) {
                    tmFactory = TrustManagerFactory.getInstance(trustAlgorithm);
                } else {
                    File trustStoreFile;
                    String s = props.get("javax.net.ssl.trustStore");
                    if (s != null) {
                        trustStoreFile = new File(s);
                        tmFactory = TrustManagerFactory.getInstance(trustAlgorithm);
                        String trustStoreProvider = props.get("javax.net.ssl.trustStoreProvider");
                        KeyStore trustStore;
                        if (trustStoreProvider != null) {
                            trustStore = KeyStore.getInstance(trustStoreType, trustStoreProvider);
                        } else {
                            trustStore = KeyStore.getInstance(trustStoreType);
                        }
                        String trustStorePassword = props.get("javax.net.ssl.trustStorePassword");
                        FileInputStream instream = new FileInputStream(trustStoreFile);
                        try {
                            trustStore.load(instream, trustStorePassword != null ? trustStorePassword.toCharArray() : null);
                        } finally {
                            instream.close();
                        }
                        tmFactory.init(trustStore);
                    } else {
                        File javaHome = new File(props.get("java.home"));
                        File file = new File(javaHome, "lib/security/jssecacerts");
                        if (!file.exists()) {
                            file = new File(javaHome, "lib/security/cacerts");
                            trustStoreFile = file;
                        } else {
                            trustStoreFile = file;
                        }
                        tmFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                        String trustStorePassword = props.get("javax.net.ssl.trustStorePassword");
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
                String keyAlgorithm = props.get("ssl.KeyManagerFactory.algorithm");
                if (keyAlgorithm == null) {
                    keyAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
                }
                String keyStoreType = props.get("javax.net.ssl.keyStoreType");
                if (keyStoreType == null) {
                    keyStoreType = KeyStore.getDefaultType();
                }
                if ("none".equalsIgnoreCase(keyStoreType)) {
                    kmFactory = KeyManagerFactory.getInstance(keyAlgorithm);
                } else {
                    File keyStoreFile = null;
                    String s = props.get("javax.net.ssl.keyStore");
                    if (s != null) {
                        keyStoreFile = new File(s);
                    }
                    if (keyStoreFile != null) {
                        kmFactory = KeyManagerFactory.getInstance(keyAlgorithm);
                        String keyStoreProvider = props.get("javax.net.ssl.keyStoreProvider");
                        KeyStore keyStore;
                        if (keyStoreProvider != null) {
                            keyStore = KeyStore.getInstance(keyStoreType, keyStoreProvider);
                        } else {
                            keyStore = KeyStore.getInstance(keyStoreType);
                        }
                        String keyStorePassword = props.get("javax.net.ssl.keyStorePassword");
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
}
