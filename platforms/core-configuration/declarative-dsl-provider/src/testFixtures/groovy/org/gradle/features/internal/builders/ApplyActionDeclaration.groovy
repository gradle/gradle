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
 * Configures the apply action of a generated plugin.
 *
 * <p>Controls which services the apply action injects and whether it reads
 * definition values eagerly (at apply time rather than lazily in a task).</p>
 *
 * <p>If no services are explicitly declared, the plugin uses its default services
 * ({@code TaskRegistrar} for project types; {@code TaskRegistrar}, {@code ProjectFeatureLayout},
 * and {@code ProviderFactory} for features).</p>
 *
 * <p>Special service names are recognized:</p>
 * <ul>
 *     <li>{@code "project"} — injects {@code Project} (an unsafe service)</li>
 *     <li>{@code "unknown"} — injects a custom {@code UnknownService} interface (for testing unknown service detection)</li>
 * </ul>
 *
 * @see PluginConfig#applyAction
 */
class ApplyActionDeclaration {
    /** Services to inject into the apply action. If empty, defaults are used. */
    List<ServiceDeclaration> injectedServices = []

    /** Whether the apply action reads definition property values eagerly via {@code .get()} at apply time. */
    boolean readsValuesEagerly = false

    /** Adds a service to inject into the apply action. */
    void injectedService(String name, Class type) {
        injectedServices.add(new ServiceDeclaration(name: name, type: type))
    }

    /**
     * Configures the apply action to eagerly read all definition property values at apply time.
     * This is used to test scenarios where the apply action reads values before the build script
     * has had a chance to configure them.
     */
    void eagerlyReadDefinitionValues() { this.readsValuesEagerly = true }
}
