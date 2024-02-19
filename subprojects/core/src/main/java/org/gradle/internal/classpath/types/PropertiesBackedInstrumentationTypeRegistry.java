/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.classpath.types;

import com.google.common.base.Splitter;
import org.gradle.internal.lazy.Lazy;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PropertiesBackedInstrumentationTypeRegistry implements InstrumentationTypeRegistry {

    private final Lazy<Map<String, Set<String>>> properties;

    private PropertiesBackedInstrumentationTypeRegistry(Supplier<Map<String, Set<String>>> properties) {
        this.properties = Lazy.locking().of(properties);
    }

    @Override
    public Set<String> getSuperTypes(String type) {
        return properties.get().getOrDefault(type, Collections.emptySet());
    }

    @Override
    public boolean isEmpty() {
        return properties.get().isEmpty();
    }

    public static InstrumentationTypeRegistry of(File properties) {
        return new PropertiesBackedInstrumentationTypeRegistry(() -> loadRegistry(properties));
    }

    private static Map<String, Set<String>> loadRegistry(File properties) {
        Map<String, Set<String>> registry = new HashMap<>();
        try (Stream<String> stream = Files.lines(properties.toPath())) {
            stream.forEach(line -> {
                String[] splitted = line.split("=");
                Set<String> superTypes = Splitter.on(",")
                    .splitToStream(splitted[1])
                    .collect(Collectors.toSet());
                registry.put(splitted[0], superTypes);
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return registry;
    }
}
