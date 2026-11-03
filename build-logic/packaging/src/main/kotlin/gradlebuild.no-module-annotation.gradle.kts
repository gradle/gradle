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

import gradlebuild.nomoduleannotation.Attributes.artifactType
import gradlebuild.nomoduleannotation.Attributes.noModuleAnnotation
import gradlebuild.nomoduleannotation.NoModuleAnnotation

val transformedArtifactNames = setOf("jspecify")

plugins.withId("java-base") {
    dependencies {
        attributesSchema {
            attribute(noModuleAnnotation)
        }
        // It would be nice if we could be more selective about which variants to apply this to.
        // TODO https://github.com/gradle/gradle/issues/11831#issuecomment-580686994
        artifactTypes.getByName("jar") {
            attributes.attribute(noModuleAnnotation, java.lang.Boolean.FALSE)
        }
        /*
         * This transform exists solely to remove ElementType.MODULE from JSpecify's annotations to allow its usage with Java 8.
         * The set parameter are used to filter artifacts to which to apply the transform, there's a single entry.
         * It would perhaps be better to do this more selectively instead of applying this transform so broadly and having
         * it just no-op in most cases.
         */
        registerTransform(NoModuleAnnotation::class) {
            from.attribute(noModuleAnnotation, false).attribute(artifactType, "jar")
            to.attribute(noModuleAnnotation, true).attribute(artifactType, "jar")
            parameters {
                artifactNames = transformedArtifactNames
            }
        }
    }
    // Not every context that applies this plugin has the libs catalog, but one that does must declare jspecify
    versionCatalogs.find("libs").ifPresent { libs ->
        val jspecify = libs.findLibrary("jspecify").get().get()
        configurations.configureEach {
            // Legacy configurations only lose roles after creation, so this covers every configuration that
            // ends up resolvable-only; the rest are never resolved.
            // The roles cannot be checked here: a legacy configuration is still consumable when this runs, so
            // checking isCanBeConsumed would skip all of them, including the distribution and tooling API
            // classpaths. A check against final roles has to run at resolution, which needs either an all {}
            // rule, forcing early graph resolution, or a beforeResolve mutation, which Gradle plans to reject.
            // Instead, the configurations third-party plugins leave consumable and resolvable are skipped by name.
            // No more are expected: none of this build's own configurations stay both consumable and resolvable.
            // If one appears, it only receives jspecify without the MODULE annotation target.
            if (isCanBeResolved && name != "jmh" && name != "shadow" && name != "gr8Gr") {
                resolutionStrategy.dependencySubstitution {
                    substitute(module("${jspecify.group}:${jspecify.name}")).using(variant(module("${jspecify.group}:${jspecify.name}:${jspecify.versionConstraint.requiredVersion}")) {
                        attributes {
                            attribute(noModuleAnnotation, true)
                        }
                    })
                }
            }
        }
    }
}
