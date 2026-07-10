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
 * Emission shape of the plugin file generated for a component.
 *
 * <p>Mutually exclusive: each plugin emits exactly one of these shapes.</p>
 */
enum PluginType {
    /**
     * Default. Emit a plugin class annotated with {@code @BindsProjectType}
     * or {@code @BindsProjectFeature}, containing a {@code Binding} inner class
     * and an {@code ApplyAction} inner class.
     */
    WITH_BINDINGS,

    /**
     * Emit a plain {@code Plugin<Project>} with an empty {@code apply()} body
     * and no binding annotation.
     */
    WITHOUT_BINDINGS,

    /**
     * Suppress plugin emission entirely. The definition file is still generated;
     * no plugin source file is written.
     */
    NO_PLUGIN
}
