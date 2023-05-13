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
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal

/**
 * A map from artifact name to a set of class name prefixes that should be kept.
 * Artifacts matched by this map will be minified to only contain the specified
 * classes and the classes they depend on. The classes are not relocated, they all
 * remain in their original namespace. This reduces the final Gradle distribution
 * size and makes us more conscious of which parts of a library we really need.
 */
val keepPatterns = mapOf(
    "fastutil" to setOf(
        // For Java compilation incremental analysis
        "it.unimi.dsi.fastutil.ints.IntOpenHashSet",
        "it.unimi.dsi.fastutil.ints.IntSets",
        // For the embedded Kotlin compiler
        "it.unimi.dsi.fastutil.ints.Int2ObjectMap",
        "it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap",
        "it.unimi.dsi.fastutil.objects.Object2IntMap",
        "it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap",
        "it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap",
        "it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap",
        "it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap",
        "it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet",
        "it.unimi.dsi.fastutil.objects.ObjectOpenHashSet",
        "it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap",
        // For dependency management
        "it.unimi.dsi.fastutil.longs.Long2ObjectMap",
        "it.unimi.dsi.fastutil.longs.Long2ObjectMaps",
        "it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap",
        // For the configuration cache module
        "it.unimi.dsi.fastutil.objects.ReferenceArrayList",
        "it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet",
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
        /*
         * This transform exists solely to shrink the size of the fastutil jar from 25MB to 1.5MB.
         * The keys to the map parameter are used as the names of the files to which to apply the transform - there is only one entry.
         * It would perhaps be better to do this more selectively instead of applying this transform so broadly and having
         * it just no-op in most cases.
         */
        registerTransform(Minify::class) {
            from.attribute(minified, false).attribute(artifactType, "jar")
            to.attribute(minified, true).attribute(artifactType, "jar")
            parameters {
                keepClassesByArtifact = keepPatterns
            }
        }
    }
    configurations.all {
        // TODO we should be able to solve this only by requesting attributes for artifacts - https://github.com/gradle/gradle/issues/11831#issuecomment-580686994
        (this as ConfigurationInternal).beforeLocking {
            // everywhere where we resolve, prefer the minified version
            if (isCanBeResolved && !isCanBeConsumed && name !in setOf("currentApiClasspath", "codenarc")) {
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
