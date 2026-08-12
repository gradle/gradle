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
 * Configures the manifests for a built-in XDCL ecosystem plugin module.
 *
 * A built-in ecosystem's plugin is applied by id (a distribution plugin), so its jar never lands on
 * the settings plugin classpath — instead its standard plugin descriptor declares the ecosystem's
 * PUBLISHED schema library as a `distribution-companion-modules` entry, and core plugin resolution
 * injects that library into the settings classpath resolution (served at the distribution version
 * by the image-embedded Maven repository). One descriptor is written per plugin carrier in the
 * module (the ids come from the `.xdcl` file names, so there is nothing to declare here). See
 * `integrations/gradle/doc/builtin-ecosystem-schemas.md`.
 */
interface XdclBuiltinEcosystemExtension {

    /**
     * The published schema library module(s) of this ecosystem (`org.gradle` artifactIds; a module
     * name doubles as the distribution module name). Defaults to the carrier's lib sibling
     * (`gradle-${project.name}` minus the `-plugin` suffix); override to point at dedicated
     * schema-only modules. Transitive schema libraries (e.g. the common ecosystem) need no entry —
     * they arrive through the published metadata.
     */
    val schemaModules: ListProperty<String>
}
