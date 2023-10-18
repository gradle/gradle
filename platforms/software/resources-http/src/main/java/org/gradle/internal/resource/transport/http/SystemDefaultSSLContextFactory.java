/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.NonNullApi;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.security.KeyStore;

/**
 * This class contains SSLContext initialization similar to what SSLContext.getDefault() does.
 * Unfortunately, SSLContext.getDefault() can not be used because it is heavily cached by the JVM,
 * including keyStore and trustStore managers.
 * There is no way to reset these caches because they are private and are initialized in static sections.
 */
@NonNullApi
public class SystemDefaultSSLContextFactory {
    private static final String NONE = "NONE";
    private static final String P11KEYSTORE = "PKCS11";

    private static final Logger LOGGER = Logging.getLogger(SystemDefaultSSLContextFactory.class);

    static SSLContext create() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(getKeyManagers(), getTrustManagers(), null);
        return context;
    }

    private static KeyManager[] getKeyManagers() throws Exception {
        String keyStorePath = System.getProperty("javax.net.ssl.keyStore", "");
        String keyStoreType = System.getProperty("javax.net.ssl.keyStoreType", KeyStore.getDefaultType());
        String keyStoreProvider = System.getProperty("javax.net.ssl.keyStoreProvider", "");
        String keyStorePasswordString = System.getProperty("javax.net.ssl.keyStorePassword", "");

        if (P11KEYSTORE.equals(keyStoreType) && !NONE.equals(keyStorePath)) {
            throw new IllegalArgumentException("if keyStoreType is " + P11KEYSTORE + ", then keyStore must be " + NONE);
        }

        char[] keystorePassword = null;
        if (!keyStorePasswordString.isEmpty()) {
            keystorePassword = keyStorePasswordString.toCharArray();
        }

        KeyStore keyStore = loadKeyStore(
            keyStorePath,
            keyStoreType,
            keyStoreProvider,
            keystorePassword,
            true
        );

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        if (P11KEYSTORE.equals(keyStoreType)) {
            keyManagerFactory.init(keyStore, null);
        } else {
            keyManagerFactory.init(keyStore, keystorePassword);
        }
        return keyManagerFactory.getKeyManagers();
    }

    @Nullable
    private static KeyStore loadKeyStore(
        String keyStorePath,
        String keyStoreType,
        String keyStoreProvider,
        @Nullable char[] keyStorePassword,
        boolean errorOnMissingFile
    ) throws Exception {
        if (keyStoreType.isEmpty()) {
            return null;
        }

        KeyStore keyStore;
        if (keyStoreProvider.isEmpty()) {
            keyStore = KeyStore.getInstance(keyStoreType);
        } else {
            keyStore = KeyStore.getInstance(keyStoreType, keyStoreProvider);
        }

        if (!keyStorePath.isEmpty() && !NONE.equals(keyStorePath)) {
            try (FileInputStream fis = new FileInputStream(keyStorePath)) {
                keyStore.load(fis, keyStorePassword);
            } catch (FileNotFoundException e) {
                if (errorOnMissingFile) {
                    throw e;
                } else {
                    return null;
                }
            }
        } else {
            keyStore.load(null, keyStorePassword);
        }
        return keyStore;
    }

    private static String getDefaultSecurityPath(){
        return System.getProperty("java.home") + File.separator + "lib" + File.separator + "security";
    }

    private static String getDefaultTrustStore(){
        return getDefaultSecurityPath() + File.separator + "cacerts";
    }

    private static String getDefaultJsseTrustStore(){
        return getDefaultSecurityPath() + File.separator + "jssecacerts";
    }

    private static TrustManager[] getTrustManagers() throws Exception {
        String storePath = System.getProperty("javax.net.ssl.trustStore", getDefaultJsseTrustStore());
        String storeType = System.getProperty("javax.net.ssl.trustStoreType", KeyStore.getDefaultType());
        String storeProvider = System.getProperty("javax.net.ssl.trustStoreProvider", "");
        String storePasswordString = System.getProperty("javax.net.ssl.trustStorePassword", "");

        KeyStore keyStore = null;
        if (!NONE.equals(storePath)) {
            String[] fileNames = new String[]{storePath, getDefaultTrustStore()};
            for (String fileName : fileNames) {
                File candidate = new File(fileName);
                if (candidate.isFile() && candidate.canRead()) {
                    storePath = fileName;
                    break;
                } else if (!fileName.equals(getDefaultJsseTrustStore())) {
                    LOGGER.warn("Trust store file {} does not exist or is not readable. This may lead to SSL connection failures.", fileName);
                }
            }

            char[] storePassword = null;
            if (!storePasswordString.isEmpty()) {
                storePassword = storePasswordString.toCharArray();
            }

            keyStore = loadKeyStore(
                storePath,
                storeType,
                storeProvider,
                storePassword,
                false
            );
        }

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);

        return trustManagerFactory.getTrustManagers();
    }
}
