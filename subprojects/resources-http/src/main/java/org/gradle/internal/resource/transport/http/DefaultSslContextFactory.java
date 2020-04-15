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
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
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
        "javax.net.ssl.keyStorePassword",
        "java.home"
    );

    private LoadingCache<Map<String, String>, SSLContext> cache = CacheBuilder.newBuilder().softValues().build(
        new SynchronizedSystemPropertiesCacheLoader(new SslContextCacheLoader())
    );

    @Override
    public SSLContext createSslContext() {
        return cache.getUnchecked(getCurrentProperties());
    }

    private Map<String, String> getCurrentProperties() {
        return SystemProperties.getInstance().withSystemProperties(new Factory<Map<String, String>>() {
            @Override
            public Map<String, String> create() {
                Map<String, String> currentProperties = new TreeMap<String, String>();
                for (String prop : SSL_SYSTEM_PROPERTIES) {
                    currentProperties.put(prop, System.getProperty(prop));
                }
                return currentProperties;
            }
        });
    }

    private static class SynchronizedSystemPropertiesCacheLoader extends CacheLoader<Map<String, String>, SSLContext> {
        private final SslContextCacheLoader delegate;

        private SynchronizedSystemPropertiesCacheLoader(SslContextCacheLoader delegate) {
            this.delegate = delegate;
        }

        @Override
        public SSLContext load(Map<String, String> props) {
            /*
             * NOTE! The JDK code to create SSLContexts relies on the values of the given system properties.
             *
             * To prevent concurrent changes to system properties from interfering with this, we need to synchronize access/modifications
             * to system properties.  This is best effort since we can't prevent user code from modifying system properties willy-nilly.
             *
             * The most critical system property is java.home. Changing this property while trying to create a SSLContext can cause many strange
             * problems:
             * https://github.com/gradle/gradle/issues/8830
             * https://github.com/gradle/gradle/issues/8039
             * https://github.com/gradle/gradle/issues/7842
             * https://github.com/gradle/gradle/issues/2588
             */
            return SystemProperties.getInstance().withSystemProperties(props, new Factory<SSLContext>() {
                @Override
                public SSLContext create() {
                    return delegate.load(props);
                }
            });
        }
    }

    private static class SslContextCacheLoader extends CacheLoader<Map<String, String>, SSLContext> {
        @Override
        public SSLContext load(Map<String, String> props) {
            // TODO: We should see if we can go back to using HttpClient again.
            // This implementation is borrowed from the Apache HttpClient project
            // https://github.com/apache/httpclient/blob/4.2.2/httpclient/src/main/java/org/apache/http/conn/ssl/SSLSocketFactory.java#L246-L354
            try {
                TrustManagerFactory tmFactory = initTrustManagerFactory(props);
                KeyManagerFactory kmFactory = initKeyManagerFactory(props);

                SSLContext sslcontext = SSLContext.getInstance("TLS");
                sslcontext.init(kmFactory.getKeyManagers(), tmFactory.getTrustManagers(), null);

                return sslcontext;
            } catch (GeneralSecurityException | IOException e) {
                throw new SSLInitializationException(e.getMessage(), e);
            }
        }

        private static TrustManagerFactory initTrustManagerFactory(Map<String, String> props) throws GeneralSecurityException, IOException {
            TrustManagerFactory tmFactory;

            String trustAlgorithm = trustAlgorithm(props);
            String trustStoreType = trustStoreType(props);
            if ("none".equalsIgnoreCase(trustStoreType)) {
                tmFactory = TrustManagerFactory.getInstance(trustAlgorithm);
            } else {
                File trustStoreFile;
                String s = props.get("javax.net.ssl.trustStore");
                if (s != null) {
                    trustStoreFile = new File(s);
                    tmFactory = TrustManagerFactory.getInstance(trustAlgorithm);
                    KeyStore trustStore = trustStore(props, trustStoreType);
                    char[] trustStorePassword = trustStorePassword(props);
                    try (FileInputStream instream = new FileInputStream(trustStoreFile)) {
                        trustStore.load(instream, trustStorePassword);
                    }
                    tmFactory.init(trustStore);
                } else {
                    trustStoreFile = trustStoreFile(props);
                    tmFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                    char[] trustStorePassword = trustStorePassword(props);
                    try (FileInputStream instream = new FileInputStream(trustStoreFile)) {
                        trustStore.load(instream, trustStorePassword);
                    }
                    tmFactory.init(trustStore);
                }
            }
            return tmFactory;
        }

        private static KeyManagerFactory initKeyManagerFactory(Map<String, String> props) throws GeneralSecurityException, IOException {
            KeyManagerFactory kmFactory = keyManagerFactory(props);

            String keyStoreType = keyStoreType(props);
            if (!"none".equalsIgnoreCase(keyStoreType)) {
                char[] keyStorePassword = keystorePassword(props);
                KeyStore keyStore = keyStore(props, keyStoreType, keyStorePassword);
                kmFactory.init(keyStore, keyStorePassword);
            }
            return kmFactory;
        }

        private static String trustStoreType(Map<String, String> props) {
            String trustStoreType = props.get("javax.net.ssl.trustStoreType");
            return trustStoreType == null ? KeyStore.getDefaultType() : trustStoreType;
        }

        private static KeyStore trustStore(Map<String, String> props, String trustStoreType) throws NoSuchProviderException, KeyStoreException {
            String trustStoreProvider = props.get("javax.net.ssl.trustStoreProvider");
            if (trustStoreProvider != null) {
                return KeyStore.getInstance(trustStoreType, trustStoreProvider);
            } else {
                return KeyStore.getInstance(trustStoreType);
            }
        }

        private static File trustStoreFile(Map<String, String> props) {
            File javaHome = new File(props.get("java.home"));
            File file = new File(javaHome, "lib/security/jssecacerts");
            if (!file.exists()) {
                file = new File(javaHome, "lib/security/cacerts");
            }
            return file;
        }

        private static String trustAlgorithm(Map<String, String> props) {
            String trustAlgorithm = props.get("ssl.TrustManagerFactory.algorithm");
            return trustAlgorithm == null ? TrustManagerFactory.getDefaultAlgorithm() : trustAlgorithm;
        }

        private static char[] trustStorePassword(Map<String, String> props) {
            String trustStorePassword = props.get("javax.net.ssl.trustStorePassword");
            return trustStorePassword != null ? trustStorePassword.toCharArray() : null;
        }

        private static KeyManagerFactory keyManagerFactory(Map<String, String> props) throws NoSuchAlgorithmException {
            String keyAlgorithm = props.get("ssl.KeyManagerFactory.algorithm");
            if (keyAlgorithm == null) {
                keyAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
            }
            return KeyManagerFactory.getInstance(keyAlgorithm);
        }

        private static String keyStoreType(Map<String, String> props) {
            String keyStoreType = props.get("javax.net.ssl.keyStoreType");
            if (keyStoreType == null) {
                keyStoreType = KeyStore.getDefaultType();
            }
            return keyStoreType;
        }

        private static KeyStore keyStore(Map<String, String> props, String keyStoreType, char[] keyStorePassword) throws NoSuchProviderException, KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
            KeyStore keyStore = getKeyStoreInstance(props, keyStoreType);
            File keyStoreFile = keyStoreFile(props);
            if (keyStoreFile != null) {
                try (FileInputStream instream = new FileInputStream(keyStoreFile)) {
                    keyStore.load(instream, keyStorePassword);
                }
            } else {
                keyStore.load(null, keyStorePassword);
            }
            return keyStore;
        }

        private static KeyStore getKeyStoreInstance(Map<String, String> props, String keyStoreType) throws NoSuchProviderException, KeyStoreException {
            String keyStoreProvider = props.get("javax.net.ssl.keyStoreProvider");
            if (keyStoreProvider != null) {
                return KeyStore.getInstance(keyStoreType, keyStoreProvider);
            } else {
                return KeyStore.getInstance(keyStoreType);
            }
        }

        private static File keyStoreFile(Map<String, String> props) {
            String keystoreFilePath = props.get("javax.net.ssl.keyStore");
            if (keystoreFilePath != null && !"none".equalsIgnoreCase(keystoreFilePath)) {
                return new File(keystoreFilePath);
            }
            return null;
        }

        private static char[] keystorePassword(Map<String, String> props) {
            String keyStorePassword = props.get("javax.net.ssl.keyStorePassword");
            return keyStorePassword != null ? keyStorePassword.toCharArray() : EMPTY_PASSWORD;
        }
    }
}
