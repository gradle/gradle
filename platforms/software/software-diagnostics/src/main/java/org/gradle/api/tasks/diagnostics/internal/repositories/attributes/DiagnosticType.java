/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.api.tasks.diagnostics.internal.repositories.attributes;

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;

/**
 * Attribute identifying which kind of diagnostic data a variant carries.
 * <p>
 * Internal-only — not part of the public API contract during incubation.
 */
public interface DiagnosticType extends Named {
    /**
     * The attribute key identifying which kind of diagnostic data a variant carries.
     */
    Attribute<DiagnosticType> DIAGNOSTIC_TYPE_ATTRIBUTE =
        Attribute.of("org.gradle.diagnostics.type", DiagnosticType.class);

    /**
     * Identifies a variant carrying repositories-report data published by every project
     * for consumption by the {@code repositories} report task.
     */
    String REPOSITORIES = "repositories";
}
