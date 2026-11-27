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

package org.gradle.features.internal.builders

/**
 * Discriminator for the binding annotation a plugin emits.
 *
 * <p>Project-type plugins emit {@code @BindsProjectType}, project-feature plugins emit
 * {@code @BindsProjectFeature}; the DSL uses this enum at configuration time to select the
 * correct {@code AbstractPluginBuilder} subclass family at build time.</p>
 */
enum PluginKind {
    /** Generates a plugin with {@code @BindsProjectType}. */
    PROJECT_TYPE,
    /** Generates a plugin with {@code @BindsProjectFeature}. */
    PROJECT_FEATURE
}
