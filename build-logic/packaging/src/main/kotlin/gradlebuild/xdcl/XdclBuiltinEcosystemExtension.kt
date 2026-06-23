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

package gradlebuild.xdcl

import org.gradle.api.provider.ListProperty

/**
 * Configures the xdcl-builtin-ecosystem manifests for a built-in XDCL ecosystem plugin module.
 *
 * A built-in ecosystem ships its schemas in the distribution but is applied by id (a distribution
 * plugin), so its jar never lands on the settings plugin classpath and the transform-over-classpath
 * schema discovery is blind to it. One `META-INF/xdcl-builtin-ecosystem/<plugin-id>.properties` is
 * written per plugin carrier in the module (the ids come from the `.xdcl` file names, so there is
 * nothing to declare here); each names the distribution schema module(s) whose jars the provider
 * resolves via `ModuleRegistry`. See `integrations/gradle/doc/builtin-ecosystem-schemas.md`.
 */
interface XdclBuiltinEcosystemExtension {

    /**
     * Distribution module names carrying this module's ecosystem schemas. Defaults to the module of
     * the project applying the convention (`gradle-${project.name}`), i.e. schemas live in the
     * plugin's own module; override to point at dedicated schema-only modules.
     */
    val schemaModules: ListProperty<String>
}
