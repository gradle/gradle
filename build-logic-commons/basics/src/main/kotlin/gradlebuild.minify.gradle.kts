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
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.kotlin.dsl.support.serviceOf

/**
 * A map from artifact name to a set of class name prefixes that should be kept.
 * Artifacts matched by this map will be minified to only contain the specified
 * classes and the classes they depend on. The classes are not relocated, they all
 * remain in their original namespace. This reduces the final Gradle distribution
 * size and makes us more conscious of which parts of a library we really need.
 *
 * WARNING: if you decide to do the minification by hand, make sure that you cover all paths of loading classes:
 * reflection, dynamic loading, etc. and understand how the library works internally.
 * These changes might break things in subtle ways otherwise.
 */
val keepPatterns = mapOf(
    "it.unimi.dsi:fastutil" to setOf(
        // For persistence cache
        "it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap",
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
        "it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap",
        "it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap",
    ),
    "com.github.jnr:jnr-constants" to setOf(
        // For signal codes
        "jnr.constants.platform.Signal",
        "jnr.constants.platform.aix.Signal",
        "jnr.constants.platform.darwin.Signal",
        "jnr.constants.platform.freebsd.Signal",
        "jnr.constants.platform.openbsd.Signal",
        "jnr.constants.platform.linux.Signal",
        "jnr.constants.platform.solaris.Signal",
        "jnr.constants.Constant",
        "jnr.constants.ConstantResolver",
    ),
)
plugins.withId("java-base") {
    dependencies {
        attributesSchema {
            attribute(minified)
        }
        // It would be nice if we could be more selective about which variants to apply this to.
        // TODO https://github.com/gradle/gradle/issues/11831#issuecomment-580686994
        artifactTypes.getByName("jar") {
            attributes.attribute(minified, java.lang.Boolean.FALSE)
        }
        /*
         * It would perhaps be better to do this more selectively instead of applying this transform so broadly and having
         * it just no-op in most cases.
         */
        registerTransform(Minify::class) {
            from.attribute(minified, false).attribute(artifactType, "jar")
            to.attribute(minified, true).attribute(artifactType, "jar")
            parameters {
                keepClassesByCoordinates = keepPatterns
            }
        }
    }
    afterEvaluate {
        // Without afterEvaluate, configurations.all runs before the configurations' roles are set.
        // This is yet another reason we need configuration factory methods.
        // workaround for https://github.com/gradle/gradle/issues/12459
        // note: constraints can't be used here because they end up in gradle module metadata
        val attributesFactory = gradle.serviceOf<AttributesFactory>()
        configurations.all {
            if (isCanBeResolved && !isCanBeConsumed) {
                resolutionStrategy.dependencySubstitution.all {
                    val requested = this.requested as? ModuleComponentSelector ?: return@all
                    keepPatterns.forEach { coordinates, _ ->
                        if ("${requested.group}:${requested.module}" == coordinates) {
                            val updated = DefaultModuleComponentSelector.withAttributes(
                                requested,
                                attributesFactory.of(minified, true)
                            )
                            useTarget(updated)
                        }
                    }
                }
            }
        }
    }
}
