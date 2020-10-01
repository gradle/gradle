/*
 * Copyright 2020 the original author or authors.
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
import gradlebuild.basics.classanalysis.Attributes.artifactType
import gradlebuild.basics.classanalysis.Attributes.minified
import gradlebuild.basics.transforms.Minify

/**
 * A map from artifact name to a set of class name prefixes that should be kept.
 * Artifacts matched by this map will be minified to only contain the specified
 * classes and the classes they depend on. The classes are not relocated, they all
 * remain in their original namespace. This reduces the final Gradle distribution
 * size and makes us more conscious of which parts of a library we really need.
 */
val keepPatterns = mapOf(
    "fastutil" to setOf(
        "it.unimi.dsi.fastutil.ints.IntOpenHashSet",
        "it.unimi.dsi.fastutil.ints.IntSets"
    )
)
plugins.withId("java-base") {
    dependencies {
        attributesSchema {
            attribute(minified)
        }
        artifactTypes.getByName("jar") {
            attributes.attribute(minified, java.lang.Boolean.FALSE)
        }
        registerTransform(Minify::class) {
            from.attribute(minified, false).attribute(artifactType, "jar")
            to.attribute(minified, true).attribute(artifactType, "jar")
            parameters {
                keepClassesByArtifact = keepPatterns
            }
        }
    }
    afterEvaluate {
        configurations.all {
            // everywhere where we resolve, prefer the minified version
            if (isCanBeResolved && !isCanBeConsumed && name != "currentApiClasspath") {
                attributes.attribute(minified, true)
            }
            // local projects are already minified
            if (isCanBeConsumed && !isCanBeResolved) {
                if (attributes.getAttribute(Category.CATEGORY_ATTRIBUTE)?.name == Category.LIBRARY
                    && attributes.getAttribute(Bundling.BUNDLING_ATTRIBUTE)?.name == Bundling.EXTERNAL
                ) {
                    attributes.attribute(minified, true)
                }
            }
        }
    }
}
