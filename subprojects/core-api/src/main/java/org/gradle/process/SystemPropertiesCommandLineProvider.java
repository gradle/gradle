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

package org.gradle.process;

import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;

import java.util.Map;
import java.util.stream.Collectors;

public abstract class SystemPropertiesCommandLineProvider<T> implements CommandLineArgumentProvider {
    private final T annotationProvider;

    public SystemPropertiesCommandLineProvider(T annotationProvider) {
        this.annotationProvider = annotationProvider;
    }

    @Nested
    T getAnnotationProvider() {
        return annotationProvider;
    }

    @Internal
    abstract public Map<String, String> getSystemProperties();

    @Override
    public Iterable<String> asArguments() {
        return getSystemProperties().entrySet().stream().map(e -> "-D" + e.getKey() + "=" + e.getValue()).collect(Collectors.toSet());
    }
}
