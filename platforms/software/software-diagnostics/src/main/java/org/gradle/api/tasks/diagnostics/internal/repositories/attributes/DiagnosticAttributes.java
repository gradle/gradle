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

/**
 * Constants for diagnostic-variant attributes that augment Gradle's existing public attributes.
 * <p>
 * Internal-only. The {@code "diagnostics"} value is NOT added to the public {@code Category} constants.
 */
public final class DiagnosticAttributes {
    /** {@link org.gradle.api.attributes.Category} value used by the repositories report's variant exchange. */
    public static final String DIAGNOSTICS_CATEGORY = "diagnostics";

    private DiagnosticAttributes() {}
}
