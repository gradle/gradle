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

package org.gradle.buildinit.templates.internal;

import org.gradle.buildinit.templates.InitProjectGenerator;
import org.gradle.buildinit.templates.InitProjectSpec;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages the available project templates.
 *
 * @since 8.11
 */
public final class TemplateRegistry {
    private final Map<InitProjectGenerator, List<InitProjectSpec>> templatesBySource;

    public TemplateRegistry(Map<InitProjectGenerator, List<InitProjectSpec>> templatesBySource) {
        this.templatesBySource = templatesBySource;
    }
    public List<InitProjectSpec> getAvailableTemplates() {
        return templatesBySource.values().stream().flatMap(List::stream).collect(Collectors.toList());
    }

    public InitProjectGenerator getProjectGenerator(InitProjectSpec spec) {
        return templatesBySource.entrySet().stream()
            .filter(entry -> entry.getValue().contains(spec))
            .findFirst()
            .map(Map.Entry::getKey)
            .orElseThrow(() -> new IllegalStateException("Spec not found in available templates"));
    }

    public boolean areTemplatesAvailable() {
        return !templatesBySource.isEmpty();
    }
}
