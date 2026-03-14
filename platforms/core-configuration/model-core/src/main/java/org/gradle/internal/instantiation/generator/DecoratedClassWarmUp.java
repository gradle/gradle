/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.instantiation.generator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Pre-generates {@code _Decorated} classes in parallel at daemon startup,
 * populating the shared cache in {@link AsmBackedClassGenerator} before the
 * first build needs them.
 */
class DecoratedClassWarmUp {

    private static final Logger LOGGER = LoggerFactory.getLogger(DecoratedClassWarmUp.class);
    private static final String RESOURCE = "decorated-types.txt";

    /**
     * Starts parallel class generation. Fire-and-forget — populates the shared
     * static cache in {@link AsmBackedClassGenerator}. Called once at daemon startup.
     */
    static void start(ClassGenerator classGenerator, Executor executor) {
        List<String> typeNames = loadTypeList();
        if (typeNames.isEmpty()) {
            return;
        }

        for (String typeName : typeNames) {
            executor.execute(() -> {
                try {
                    Class<?> type = Class.forName(typeName);
                    classGenerator.generate(type);
                } catch (ClassNotFoundException e) {
                    // Not on classpath in this distribution — skip silently
                } catch (Exception e) {
                    LOGGER.debug("Failed to warm up decorated class: {}", typeName, e);
                }
            });
        }
    }

    private static List<String> loadTypeList() {
        try (InputStream is = DecoratedClassWarmUp.class.getResourceAsStream(RESOURCE)) {
            if (is == null) {
                return Collections.emptyList();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toList());
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to load decorated types list", e);
            return Collections.emptyList();
        }
    }
}
