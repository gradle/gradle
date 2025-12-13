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
    afterEvaluate {
        // Without afterEvaluate, configurations.all runs before the configurations' roles are set.
        // This is yet another reason we need configuration factory methods.
        configurations.all {
            if (isCanBeResolved && !isCanBeConsumed) {
                resolutionStrategy.dependencySubstitution {
                    substitute(module("org.jspecify:jspecify")).using(variant(module("org.jspecify:jspecify:1.0.0")) {
                        attributes {
                            attribute(noModuleAnnotation, true)
                        }
                    })
                }
            }
        }
    }
}
