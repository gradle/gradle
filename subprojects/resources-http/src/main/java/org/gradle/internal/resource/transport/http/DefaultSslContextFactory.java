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
import org.gradle.api.NonNullApi;
import org.gradle.internal.SystemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@NonNullApi
public class DefaultSslContextFactory implements SslContextFactory {
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

    private final LoadingCache<Map<String, String>, SSLContext> cache = CacheBuilder.newBuilder().softValues().build(
        new SynchronizedSystemPropertiesCacheLoader()
    );

    @Override
    public SSLContext createSslContext() {
        return cache.getUnchecked(getCurrentProperties());
    }

    private static Map<String, String> getCurrentProperties() {
        return SystemProperties.getInstance().withSystemProperties(() -> {
            Map<String, String> currentProperties = new TreeMap<>();
            for (String prop : SSL_SYSTEM_PROPERTIES) {
                currentProperties.put(prop, System.getProperty(prop));
            }
            return currentProperties;
        });
    }

    private static class SynchronizedSystemPropertiesCacheLoader extends CacheLoader<Map<String, String>, SSLContext> {
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
            return SystemProperties.getInstance().withSystemProperties(props, () -> SslContextLoader.load(props));
        }
    }

    @NonNullApi
    private static class SslContextLoader {
        private static final Logger LOGGER = LoggerFactory.getLogger(SslContextLoader.class);

        public static SSLContext load(Map<String, String> props) {
            try {
                return SystemDefaultSSLContextFactory.create();
            } catch (Exception e) {
                LOGGER.error("Could not initialize SSL context. Used properties: {}", props);
                throw new SSLInitializationException(e.getMessage(), e);
            }
        }
    }
}
