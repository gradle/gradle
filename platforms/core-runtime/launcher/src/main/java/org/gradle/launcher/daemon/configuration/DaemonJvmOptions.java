/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.launcher.daemon.configuration;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.cache.internal.HeapProportionalCacheSizer;
import org.gradle.process.internal.CurrentProcess;
import org.gradle.process.internal.JvmOptions;
import org.gradle.util.internal.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.ImmutableList.toImmutableList;

public class DaemonJvmOptions extends JvmOptions {

    public static final String SSL_KEYSTORE_KEY = "javax.net.ssl.keyStore";
    public static final String SSL_KEYSTOREPASSWORD_KEY = "javax.net.ssl.keyStorePassword";
    public static final String SSL_KEYSTORETYPE_KEY = "javax.net.ssl.keyStoreType";
    public static final String SSL_TRUSTSTORE_KEY = "javax.net.ssl.trustStore";
    public static final String SSL_TRUSTPASSWORD_KEY = "javax.net.ssl.trustStorePassword";
    public static final String SSL_TRUSTSTORETYPE_KEY = "javax.net.ssl.trustStoreType";

    public static final Set<String> IMMUTABLE_DAEMON_SYSTEM_PROPERTIES = ImmutableSet.of(
        SSL_KEYSTORE_KEY, SSL_KEYSTOREPASSWORD_KEY, SSL_KEYSTORETYPE_KEY, SSL_TRUSTPASSWORD_KEY, SSL_TRUSTSTORE_KEY, SSL_TRUSTSTORETYPE_KEY, HeapProportionalCacheSizer.CACHE_RESERVED_SYSTEM_PROPERTY
    );

    public DaemonJvmOptions(FileCollectionFactory fileCollectionFactory) {
        super(fileCollectionFactory);
        final JvmOptions currentProcessJvmOptions = new CurrentProcess(fileCollectionFactory).getJvmOptions();
        systemProperties(currentProcessJvmOptions.getImmutableSystemProperties());
        handleDaemonImmutableProperties(currentProcessJvmOptions.getMutableSystemProperties());
    }

    private void handleDaemonImmutableProperties(Map<String, Object> systemProperties) {
        for (Map.Entry<String, ?> entry : systemProperties.entrySet()) {
            if (IMMUTABLE_DAEMON_SYSTEM_PROPERTIES.contains(entry.getKey())) {
                immutableSystemProperties.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public Map<String, Object> getImmutableDaemonProperties() {
        return CollectionUtils.filter(immutableSystemProperties, element ->
            IMMUTABLE_DAEMON_SYSTEM_PROPERTIES.contains(element.getKey())
        );
    }

    @Override
    public void systemProperty(String name, Object value) {
        if (IMMUTABLE_DAEMON_SYSTEM_PROPERTIES.contains(name)) {
            immutableSystemProperties.put(name, value);
        } else {
            super.systemProperty(name, value);
        }
    }

    public List<String> getAllSingleUseImmutableJvmArgs() {
        List<String> immutableDaemonParameters = new ArrayList<>();
        formatSystemProperties(getImmutableDaemonProperties(), immutableDaemonParameters);

        return getAllImmutableJvmArgs().stream()
            .filter(arg -> !immutableDaemonParameters.contains(arg))
            .collect(toImmutableList());

    }
}
