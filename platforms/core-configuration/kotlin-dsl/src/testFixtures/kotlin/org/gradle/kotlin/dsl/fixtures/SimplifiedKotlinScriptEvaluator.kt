/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.fixtures

import org.gradle.api.internal.cache.CacheConfigurationsInternal
import org.gradle.api.internal.cache.CacheResourceConfigurationInternal
import org.gradle.api.internal.classpath.RuntimeApiInfo
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.temp.GradleUserHomeTemporaryFileProvider
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.provider.Providers
import org.gradle.cache.CleanupFrequency
import org.gradle.cache.IndexedCacheParameters
import org.gradle.cache.internal.DefaultCacheCleanupStrategyFactory
import org.gradle.cache.internal.DefaultFineGrainedCacheCleanupStrategyFactory
import org.gradle.cache.internal.DefaultInMemoryCacheDecoratorFactory
import org.gradle.cache.internal.DefaultUnscopedCacheBuilderFactory
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.cache.internal.scopes.DefaultGlobalScopedCacheBuilderFactory
import org.gradle.configuration.DefaultImportsReader
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.initialization.ClassLoaderScopeOrigin
import org.gradle.internal.Describables
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classloader.DefaultClassLoaderFactory
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.file.nio.ModificationTimeFileAccessTimeJournal
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.internal.resource.StringTextResource
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.kotlin.dsl.cache.KotlinDslClasspathEntrySnapshotCache
import org.gradle.kotlin.dsl.cache.KotlinDslClasspathEntrySnapshotStore
import org.gradle.kotlin.dsl.cache.KotlinDslIncrementalCompilationCache
import org.gradle.kotlin.dsl.cache.KotlinDslIncrementalCompilationStore
import org.gradle.kotlin.dsl.execution.CompiledScript
import org.gradle.kotlin.dsl.execution.Interpreter
import org.gradle.kotlin.dsl.execution.KotlinMetadataCompatibilityChecker
import org.gradle.kotlin.dsl.execution.ProgramId
import org.gradle.kotlin.dsl.execution.ProgramTarget
import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.KotlinCompilerOptions
import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.testfixtures.internal.TestInMemoryCacheFactory
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.function.Supplier


/**
 * Evaluates the given Kotlin [script] against the given [target] writing compiled classes
 * to sub-directories of [baseCacheDir].
 */
fun eval(
    script: String,
    target: Any,
    buildTreeRootDir: File,
    baseCacheDir: File,
    baseTempDir: File,
    scriptCompilationClassPath: ClassPath = testRuntimeClassPath,
    scriptRuntimeClassPath: ClassPath = ClassPath.EMPTY
) {
    SimplifiedKotlinScriptEvaluator(
        buildTreeRootDir,
        baseCacheDir,
        baseTempDir,
        scriptCompilationClassPath,
        scriptRuntimeClassPath = scriptRuntimeClassPath
    ).use {
        it.eval(script, target)
    }
}


/**
 * A simplified Service Registry, suitable for cheaper testing of the DSL outside of Gradle.
 */
private
fun simplifiedKotlinDefaultServiceRegistry(
    baseTempDir: File,
): ServiceRegistry {

    return ServiceRegistryBuilder.builder()
        .displayName("test registry")
        .provider { registration ->
            registration.add(GradleUserHomeTemporaryFileProvider::class.java, GradleUserHomeTemporaryFileProvider { baseTempDir })
            registration.add(KotlinMetadataCompatibilityChecker::class.java, object : KotlinMetadataCompatibilityChecker {
                override fun incompatibleClasspathElements(classPath: ClassPath): List<File> = listOf()
            })
        }
        .build()
}


/**
 * A simplified Kotlin script evaluator, suitable for cheaper testing of the DSL outside Gradle.
 */
private
class SimplifiedKotlinScriptEvaluator(
    private val buildTreeRootDirFile: File,
    private val baseCacheDir: File,
    private val baseTempDir: File,
    private val scriptCompilationClassPath: ClassPath,
    private val serviceRegistry: ServiceRegistry = simplifiedKotlinDefaultServiceRegistry(baseTempDir),
    private val scriptRuntimeClassPath: ClassPath = ClassPath.EMPTY
) : AutoCloseable {

    fun eval(script: String, target: Any, topLevelScript: Boolean = false) {
        Interpreter(
            InterpreterHost(),
            TestBuildOperationRunner(),
            TestModuleRegistry(),
            DefaultClassLoaderFactory(),
            TestFiles.fileSystemAccess(),
            sharedTestClasspathSnapshotCache,
            sharedTestIncrementalCompilationCache
        ).eval(
            target,
            scriptSourceFor(script),
            Hashing.md5().hashString(script),
            mock(),
            targetScope,
            baseScope,
            topLevelScript
        )
    }

    override fun close() {
        classLoaders.forEach(URLClassLoader::close)
    }

    private
    val classLoaders = mutableListOf<URLClassLoader>()

    private
    val parentClassLoader = mock<ClassLoader>()

    private
    val baseScope = mock<ClassLoaderScope>(name = "baseScope") {
        on { exportClassLoader } doReturn parentClassLoader
    }

    private
    val targetScopeExportClassLoader = mock<ClassLoader>()

    private
    val targetScope = mock<ClassLoaderScope>(name = "targetScope") {
        on { parent } doReturn baseScope
        on { exportClassLoader } doReturn targetScopeExportClassLoader
    }

    private
    fun scriptSourceFor(script: String): ScriptSource = mock {
        // Derive fileName from a hash of the script + this evaluator's classpath so that
        // distinct (script, classpath) pairs get distinct scriptIdentities downstream
        // (compileScript builds `"$fileName#$stage"`). Without this, every test fixture
        // evaluator shared `script.gradle.kts` and polluted BTA's per-identity IC state
        // through [sharedTestIncrementalCompilationCache] — different tests compiling
        // different scripts under the same identity made IC's source-snapshot / output dir
        // unsafe to share. The hash isolates them; classpath snapshotting (the dominant
        // cold cost) is still amortised because that's keyed by classpath-entry content.
        val classpathFingerprint = scriptCompilationClassPath.asFiles.joinToString { it.absolutePath }
        val identityHash = Hashing.md5().hashString(script + "\u0000" + classpathFingerprint)
        on { fileName } doReturn "$identityHash/script.gradle.kts"
        on { className } doReturn "Script_gradle"
        on { shortDisplayName } doReturn Describables.of("<test script>")
        on { resource } doReturn StringTextResource("<test script>", script)
    }

    private
    inner class InterpreterHost : Interpreter.Host {

        override val buildTreeRootDir: Path
            get() = buildTreeRootDirFile.toPath()

        override fun serviceRegistryFor(programTarget: ProgramTarget, target: Any): ServiceRegistry =
            serviceRegistry

        override fun cachedClassFor(programId: ProgramId): CompiledScript? =
            null

        override fun cache(specializedProgram: CompiledScript, programId: ProgramId) = Unit

        override fun cachedDirFor(
            scriptHost: KotlinScriptHost<*>,
            programId: ProgramId,
            compilationClassPath: ClassPath,
            accessorsClassPath: ClassPath,
            initializer: (File) -> Unit
        ): File = baseCacheDir.resolve(programId.sourceHash.toString()).resolve(programId.templateId).also { cacheDir ->
            cacheDir.mkdirs()
            initializer(cacheDir)
        }

        override fun compilationClassPathOf(classLoaderScope: ClassLoaderScope): ClassPath =
            scriptCompilationClassPath

        override fun stage1BlocksAccessorsFor(scriptHost: KotlinScriptHost<*>): ClassPath =
            ClassPath.EMPTY

        override fun accessorsClassPathFor(scriptHost: KotlinScriptHost<*>): ClassPath =
            ClassPath.EMPTY

        override fun startCompilerOperation(description: String): AutoCloseable =
            mock()

        override fun loadClassInChildScopeOf(
            classLoaderScope: ClassLoaderScope,
            childScopeId: String,
            origin: ClassLoaderScopeOrigin,
            location: File,
            className: String,
            accessorsClassPath: ClassPath
        ): CompiledScript =
            DummyCompiledScript(
                classLoaderFor(scriptRuntimeClassPath + DefaultClassPath.of(location))
                    .also { classLoaders += it }
                    .loadClass(className)
            )

        override fun applyPluginsTo(scriptHost: KotlinScriptHost<*>, pluginRequests: PluginRequests) = Unit

        override fun applyBasePluginsTo(project: ProjectInternal) = Unit

        override fun setupEmbeddedKotlinFor(scriptHost: KotlinScriptHost<*>) = Unit

        override fun closeTargetScopeOf(scriptHost: KotlinScriptHost<*>) = Unit

        override fun hashOf(classPath: ClassPath): HashCode =
            TestHashCodes.hashCodeFrom(0)

        override fun runCompileBuildOperation(scriptPath: String, stage: String, action: () -> String): String =
            action()

        override fun onScriptClassLoaded(scriptSource: ScriptSource, specializedProgram: Class<*>) = Unit

        override val implicitImports: List<String>
            get() = ImplicitImports(DefaultImportsReader(RuntimeApiInfo(InterpreterHost::class.java.classLoader))).list

        override val compilerOptions: KotlinCompilerOptions
            get() = KotlinCompilerOptions()
    }
}


class DummyCompiledScript(override val program: Class<*>) : CompiledScript {

    override val classPath: ClassPath
        get() = ClassPath.EMPTY

    override fun onReuse() {
    }

    override fun equals(other: Any?) = when {
        other === this -> true
        other == null || other.javaClass != this.javaClass -> false
        else -> program == (other as DummyCompiledScript).program
    }
}


val testRuntimeClassPath: ClassPath by lazy {
    ClasspathUtil.getClasspath(SimplifiedKotlinScriptEvaluator::class.java.classLoader)
}


/**
 * A test-only [KotlinDslIncrementalCompilationCache] and [KotlinDslClasspathEntrySnapshotCache] paired
 * with the [Closeable] stores that own their underlying [org.gradle.cache.PersistentCache]s. Tests
 * must [close] this once they are done, or the file locks and on-disk state will leak for the
 * duration of the test JVM.
 */
class TestIncrementalCompilationCache internal constructor(
    val incrementalCompilationCache: KotlinDslIncrementalCompilationCache,
    private val incrementalCompilationStore: KotlinDslIncrementalCompilationStore,
    val classpathEntrySnapshotCache: KotlinDslClasspathEntrySnapshotCache,
    private val classpathEntrySnapshotStore: KotlinDslClasspathEntrySnapshotStore,
) : AutoCloseable {
    override fun close() {
        incrementalCompilationStore.close()
        classpathEntrySnapshotStore.close()
    }
}


/**
 * Builds a real [KotlinDslIncrementalCompilationCache] and [KotlinDslClasspathEntrySnapshotCache] for
 * tests, rooted at [rootDir]. Wires the same primitives Gradle uses in production but with in-memory
 * test fakes for the cache factories, so each test gets isolated caches without touching the real
 * user-home cache.
 *
 * Returns an [AutoCloseable] wrapper — the caller is responsible for closing it (typically in
 * `@After` / `@AfterClass`, or via `close()` on whatever owns the cache).
 */
fun testIncrementalCompilationCache(rootDir: File): TestIncrementalCompilationCache {
    rootDir.mkdirs()
    val cacheBuilderFactory = DefaultGlobalScopedCacheBuilderFactory(rootDir, DefaultUnscopedCacheBuilderFactory(TestInMemoryCacheFactory()))
    val inMemoryCacheDecoratorFactory = DefaultInMemoryCacheDecoratorFactory(false, TestCrossBuildInMemoryCacheFactory())
    val fileAccessTimeJournal = ModificationTimeFileAccessTimeJournal()
    val cacheCleanupStrategyFactory = DefaultFineGrainedCacheCleanupStrategyFactory(
        DefaultCacheCleanupStrategyFactory(TestBuildOperationRunner()),
        fileAccessTimeJournal
    )
    // These caches back unit tests, not a real Gradle invocation, so age-based eviction has no place
    // here: NEVER means close() never sweeps. It keeps tests deterministic (the per-JVM shared cache
    // is reused across the whole suite, and cleanup tests drive the soft/hard-delete passes themselves
    // rather than racing a sweep on close). The retention supplier is therefore never consulted.
    val createdResources = mock<CacheResourceConfigurationInternal> {
        on { entryRetentionTimestampSupplier } doReturn Supplier { 0L }
    }
    val cacheConfigurations = mock<CacheConfigurationsInternal> {
        on { getCreatedResources() } doReturn createdResources
        on { cleanupFrequency } doReturn Providers.of(CleanupFrequency.NEVER)
    }
    val store = KotlinDslIncrementalCompilationStore(cacheBuilderFactory, fileAccessTimeJournal, cacheConfigurations, cacheCleanupStrategyFactory)
    val cache = KotlinDslIncrementalCompilationCache(store.cache, store.fileAccessTracker, store.softDeleter)
    val snapshotStore = KotlinDslClasspathEntrySnapshotStore(cacheBuilderFactory, inMemoryCacheDecoratorFactory)
    val snapshotCache = KotlinDslClasspathEntrySnapshotCache(
        snapshotStore.snapshotsCacheDirectory,
        snapshotStore.createIndexedCache(
            IndexedCacheParameters.of("kotlinDslClasspathSnapshotIndex", HashCode::class.java, HashCodeSerializer()),
            10_000,
            true
        )
    )
    return TestIncrementalCompilationCache(cache, store, snapshotCache, snapshotStore)
}


/**
 * A per-JVM [KotlinDslIncrementalCompilationCache] and [KotlinDslClasspathEntrySnapshotCache] for unit
 * tests. The classpath snapshotting that `configureIncrementalCompilation` does is ~5 s for the
 * kotlin-dsl test runtime classpath; the lazy here amortises it across every test in the same JVM
 * fork.
 *
 * Rooted at a unique per-JVM directory under `java.io.tmpdir` (PID-suffixed) so concurrent Gradle
 * test forks don't contend on the same on-disk cache lock — without per-fork isolation,
 * [org.gradle.cache.PersistentCache]'s exclusive-on-write lock effectively serialises the test
 * forks. The directory is deleted on JVM shutdown.
 *
 * Tests that need cache isolation (e.g. asserting on cache-state side effects) should use
 * [testIncrementalCompilationCache] with their own directory instead.
 */
private val sharedTestCaches: TestIncrementalCompilationCache by lazy {
    val pid = ProcessHandle.current().pid()
    val dir = File(System.getProperty("java.io.tmpdir"), "kotlin-dsl-ic-test-$pid").also { it.mkdirs() }
    val testCache = testIncrementalCompilationCache(dir)
    Runtime.getRuntime().addShutdownHook(Thread {
        try {
            testCache.close()
        } catch (_: Throwable) {
        }
        try {
            dir.deleteRecursively()
        } catch (_: Throwable) {
        }
    })
    testCache
}


val sharedTestIncrementalCompilationCache: KotlinDslIncrementalCompilationCache
    get() = sharedTestCaches.incrementalCompilationCache


val sharedTestClasspathSnapshotCache: KotlinDslClasspathEntrySnapshotCache
    get() = sharedTestCaches.classpathEntrySnapshotCache
