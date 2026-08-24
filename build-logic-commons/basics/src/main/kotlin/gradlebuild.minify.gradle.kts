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
import org.gradle.api.JavaVersion
import gradlebuild.basics.classanalysis.Attributes.artifactType
import gradlebuild.basics.classanalysis.Attributes.minified
import gradlebuild.basics.transforms.Minify
import gradlebuild.basics.transforms.MinifySpec
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
val minifyPatterns = mapOf(
    "it.unimi.dsi:fastutil" to MinifySpec(
        keepClasses = setOf(
            // What the distribution refers to, and what declares the members it inherits: the
            // minifier keeps every member of a class named here, and only what is reachable of the
            // rest. Derived by reading the references to this library out of the distribution.
            "it.unimi.dsi.fastutil.Function",
            "it.unimi.dsi.fastutil.Stack",
            "it.unimi.dsi.fastutil.ints.AbstractInt2LongFunction",
            "it.unimi.dsi.fastutil.ints.AbstractInt2LongMap",
            "it.unimi.dsi.fastutil.ints.AbstractInt2ObjectFunction",
            "it.unimi.dsi.fastutil.ints.AbstractInt2ObjectMap",
            "it.unimi.dsi.fastutil.ints.AbstractInt2ReferenceFunction",
            "it.unimi.dsi.fastutil.ints.AbstractInt2ReferenceMap",
            "it.unimi.dsi.fastutil.ints.AbstractIntCollection",
            "it.unimi.dsi.fastutil.ints.AbstractIntList",
            "it.unimi.dsi.fastutil.ints.AbstractIntSet",
            "it.unimi.dsi.fastutil.ints.Int2IntFunction",
            "it.unimi.dsi.fastutil.ints.Int2IntMap",
            "it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap",
            "it.unimi.dsi.fastutil.ints.Int2LongFunction",
            "it.unimi.dsi.fastutil.ints.Int2LongMap",
            "it.unimi.dsi.fastutil.ints.Int2LongMap\$Entry",
            "it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap",
            "it.unimi.dsi.fastutil.ints.Int2ObjectFunction",
            "it.unimi.dsi.fastutil.ints.Int2ObjectMap",
            "it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap",
            "it.unimi.dsi.fastutil.ints.Int2ReferenceFunction",
            "it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap",
            "it.unimi.dsi.fastutil.ints.IntArrayList",
            "it.unimi.dsi.fastutil.ints.IntCollection",
            "it.unimi.dsi.fastutil.ints.IntIterable",
            "it.unimi.dsi.fastutil.ints.IntIterator",
            "it.unimi.dsi.fastutil.ints.IntList",
            "it.unimi.dsi.fastutil.ints.IntOpenHashSet",
            "it.unimi.dsi.fastutil.ints.IntSet",
            "it.unimi.dsi.fastutil.ints.IntSets",
            "it.unimi.dsi.fastutil.ints.IntStack",
            "it.unimi.dsi.fastutil.longs.AbstractLong2IntFunction",
            "it.unimi.dsi.fastutil.longs.AbstractLong2IntMap",
            "it.unimi.dsi.fastutil.longs.AbstractLong2ObjectFunction",
            "it.unimi.dsi.fastutil.longs.AbstractLong2ObjectMap",
            "it.unimi.dsi.fastutil.longs.AbstractLongCollection",
            "it.unimi.dsi.fastutil.longs.AbstractLongList",
            "it.unimi.dsi.fastutil.longs.AbstractLongSet",
            "it.unimi.dsi.fastutil.longs.Long2IntFunction",
            "it.unimi.dsi.fastutil.longs.Long2IntMap",
            "it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap",
            "it.unimi.dsi.fastutil.longs.Long2ObjectFunction",
            "it.unimi.dsi.fastutil.longs.Long2ObjectMap",
            "it.unimi.dsi.fastutil.longs.Long2ObjectMaps",
            "it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap",
            "it.unimi.dsi.fastutil.longs.LongArrayList",
            "it.unimi.dsi.fastutil.longs.LongCollection",
            "it.unimi.dsi.fastutil.longs.LongList",
            "it.unimi.dsi.fastutil.longs.LongOpenHashSet",
            "it.unimi.dsi.fastutil.longs.LongSet",
            "it.unimi.dsi.fastutil.longs.LongSets",
            "it.unimi.dsi.fastutil.objects.AbstractObject2IntFunction",
            "it.unimi.dsi.fastutil.objects.AbstractObject2IntMap",
            "it.unimi.dsi.fastutil.objects.AbstractObjectCollection",
            "it.unimi.dsi.fastutil.objects.AbstractObjectSet",
            "it.unimi.dsi.fastutil.objects.AbstractReference2IntFunction",
            "it.unimi.dsi.fastutil.objects.AbstractReference2IntMap",
            "it.unimi.dsi.fastutil.objects.AbstractReference2ObjectFunction",
            "it.unimi.dsi.fastutil.objects.AbstractReference2ObjectMap",
            "it.unimi.dsi.fastutil.objects.AbstractReferenceCollection",
            "it.unimi.dsi.fastutil.objects.AbstractReferenceList",
            "it.unimi.dsi.fastutil.objects.AbstractReferenceSet",
            "it.unimi.dsi.fastutil.objects.Object2IntFunction",
            "it.unimi.dsi.fastutil.objects.Object2IntMap",
            "it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap",
            "it.unimi.dsi.fastutil.objects.ObjectCollection",
            "it.unimi.dsi.fastutil.objects.ObjectIterable",
            "it.unimi.dsi.fastutil.objects.ObjectIterators",
            "it.unimi.dsi.fastutil.objects.ObjectListIterator",
            "it.unimi.dsi.fastutil.objects.ObjectOpenHashSet",
            "it.unimi.dsi.fastutil.objects.ObjectSet",
            "it.unimi.dsi.fastutil.objects.Reference2IntFunction",
            "it.unimi.dsi.fastutil.objects.Reference2IntMap",
            "it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap",
            "it.unimi.dsi.fastutil.objects.Reference2ObjectFunction",
            "it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap",
            "it.unimi.dsi.fastutil.objects.ReferenceArrayList",
            "it.unimi.dsi.fastutil.objects.ReferenceList",
            "it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet",
        ),
        removePackages = setOf(
            // Reading and writing collections as files, which the distribution never asks for.
            "it.unimi.dsi.fastutil.io",
        ),
        dropLocalVariables = true,
    ),
    "org.bouncycastle:bcprov-jdk18on" to MinifySpec(
        keepClasses = setOf(
            "org.bouncycastle.**",
            // used by Apache MINA sshd and jsch
            "org.bouncycastle.pqc.crypto.mlkem.**",
            "org.bouncycastle.pqc.crypto.ntruprime.**",
        ),
        excludedClasses = setOf(
            "org.bouncycastle.pqc.**",
            "org.bouncycastle.jcajce.provider.asymmetric.Dilithium**",
            "org.bouncycastle.jcajce.provider.asymmetric.Falcon**",
            "org.bouncycastle.jcajce.provider.asymmetric.NTRU**",
            "org.bouncycastle.jcajce.provider.asymmetric.SLHDSA**",
            "org.bouncycastle.jcajce.provider.asymmetric.SPHINCSPlus**",
            "org.bouncycastle.jcajce.provider.asymmetric.mldsa.**",
            "org.bouncycastle.jcajce.provider.asymmetric.slhdsa.**",
            "org.bouncycastle.jcajce.provider.asymmetric.mlkem.**",
            "org.bouncycastle.crypto.signers.mldsa.**",
            "org.bouncycastle.crypto.signers.slhdsa.**",
            "org.bouncycastle.crypto.signers.Hash**",
            "org.bouncycastle.crypto.signers.SLHDSASigner",
            "org.bouncycastle.crypto.kems.mlkem.**",
        ),
        removePackages = setOf(
            "org.bouncycastle.pqc.jcajce",
            "org.bouncycastle.pqc.legacy",
        ),
        dropLocalVariables = true,
        sideEffectFreeCalls = setOf(
            // The provider skips algorithms whose classes are absent, because it looks them up by name.
            // This is the one place where it names post-quantum classes directly.
            "org.bouncycastle.jce.provider.BouncyCastleProvider#void loadPQCKeys()",
        ),
    ),
    "org.jetbrains.kotlin:kotlin-compiler-embeddable" to MinifySpec(
        // The compiler resolves its own services by name, so nothing here is unreachable. R8 cannot
        // process it either
        removeUnreachable = false,
        dropLocalVariables = true,
        removePackages = setOf(
            "org.jetbrains.kotlin.analysis.decompiler.js",
            "org.jetbrains.kotlin.analysis.decompiler.konan",
            "org.jetbrains.kotlin.backend.konan",
            "org.jetbrains.kotlin.backend.wasm",
            "org.jetbrains.kotlin.builtins.konan",
            "org.jetbrains.kotlin.descriptors.konan",
            "org.jetbrains.kotlin.fir.analysis.diagnostics.js",
            "org.jetbrains.kotlin.fir.analysis.diagnostics.native",
            "org.jetbrains.kotlin.fir.analysis.diagnostics.wasm",
            "org.jetbrains.kotlin.fir.analysis.js",
            "org.jetbrains.kotlin.fir.analysis.native",
            "org.jetbrains.kotlin.fir.analysis.wasm",
            "org.jetbrains.kotlin.fir.backend.native",
            "org.jetbrains.kotlin.frontend.js",
            "org.jetbrains.kotlin.incremental.js",
            "org.jetbrains.kotlin.ir.backend.js",
            "org.jetbrains.kotlin.ir.inline.konan",
            "org.jetbrains.kotlin.js",
            "org.jetbrains.kotlin.metadata.js",
            "org.jetbrains.kotlin.native",
            "org.jetbrains.kotlin.resolve.konan",
            "org.jetbrains.kotlin.serialization.js",
            "org.jetbrains.kotlin.wasm",
        ),
    ),
    "com.github.jnr:jnr-constants" to MinifySpec(
        dropLocalVariables = true,
        keepClasses = setOf(
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
    ),
)

val libraryDependencies = configurations.resolvable("minifierLibraries") {
    // Of the libraries that are minified, the Kotlin compiler is the only one that refers to anything
    // outside itself, and the minifier stops at the first supertype it cannot find
    versionCatalogs.find("libs").flatMap { it.findLibrary("kotlinCompilerEmbeddable") }.ifPresent(dependencies::addLater)
}

val minifier = configurations.resolvable("minifier") {
    versionCatalogs.find("buildLibs").flatMap { it.findLibrary("proguard") }.ifPresent(dependencies::addLater)
    exclude(group = "org.json")
}

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
                minifySpecsByCoordinates = minifyPatterns
                minifierClasspath.from(minifier)
                minifiedLibraries.from(libraryDependencies)
                platformLibrary = layout.settingsDirectory.file("build/minifier/platform-${JavaVersion.current().majorVersion}.jar")
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
            if (isCanBeResolved && !isCanBeConsumed && name != libraryDependencies.name && !name.startsWith("jmh")) {
                resolutionStrategy.dependencySubstitution.all {
                    val requested = this.requested as? ModuleComponentSelector ?: return@all
                    minifyPatterns.forEach { coordinates, _ ->
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
