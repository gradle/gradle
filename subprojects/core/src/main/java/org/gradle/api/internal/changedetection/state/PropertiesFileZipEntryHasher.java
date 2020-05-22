/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Properties;

public class PropertiesFileZipEntryHasher implements ZipEntryHasher, ConfigurableNormalizer {
    private final ResourceEntryFilter propertyResourceFilter;

    public PropertiesFileZipEntryHasher(ResourceEntryFilter propertyResourceFilter) {
        this.propertyResourceFilter = propertyResourceFilter;
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        propertyResourceFilter.appendConfigurationToHasher(hasher);
    }

    @Nullable
    @Override
    public HashCode hash(ZipEntryContext zipEntryContext) throws IOException {
        return hashProperties(ByteStreams.toByteArray(zipEntryContext.getEntry().getInputStream()));
    }

    private HashCode hashProperties(byte[] entryBytes) throws IOException {
        Hasher hasher = Hashing.newHasher();
        Properties properties = new Properties();
        properties.load(new InputStreamReader(new ByteArrayInputStream(entryBytes), new PropertyResourceBundleFallbackCharset()));
        Map<String, String> entries = Maps.fromProperties(properties);
        entries
            .entrySet()
            .stream()
            .filter(entry ->
                !propertyResourceFilter.shouldBeIgnored(entry.getKey()))
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                hasher.putString(entry.getKey());
                hasher.putString(entry.getValue());
            });
        return hasher.hash();
    }
}
