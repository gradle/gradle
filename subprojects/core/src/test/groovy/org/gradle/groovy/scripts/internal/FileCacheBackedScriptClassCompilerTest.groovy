/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.groovy.scripts.internal

import org.gradle.api.Action
import org.gradle.api.internal.changedetection.state.CachingFileSnapshotter
import org.gradle.api.internal.initialization.ClassLoaderIds
import org.gradle.cache.CacheBuilder
import org.gradle.cache.CacheRepository
import org.gradle.cache.CacheValidator
import org.gradle.cache.PersistentCache
import org.gradle.groovy.scripts.Script
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.Transformer
import org.gradle.internal.resource.Resource
import org.gradle.logging.ProgressLogger
import org.gradle.logging.ProgressLoggerFactory
import spock.lang.Specification

class FileCacheBackedScriptClassCompilerTest extends Specification {
    final ScriptCompilationHandler scriptCompilationHandler = Mock()
    final CacheRepository cacheRepository = Mock()
    final CacheBuilder cacheBuilder = Mock()
    final CacheValidator validator = Mock()
    final PersistentCache cache = Mock()
    final ScriptSource source = Mock()
    final ClassLoader classLoader = Mock()
    final Transformer transformer = Mock()
    final CompileOperation<?> operation = Mock()
    final CachingFileSnapshotter snapshotter = Mock()
    final File cacheDir = new File("base-dir")
    final File classesDir = new File(cacheDir, "classes")
    final File metadataDir = new File(cacheDir, "metadata")
    final FileCacheBackedScriptClassCompiler compiler = new FileCacheBackedScriptClassCompiler(cacheRepository, validator, scriptCompilationHandler, Stub(ProgressLoggerFactory), snapshotter)
    final Action verifier = Stub()
    final CompiledScript compiledScript = Stub() {
        loadClass() >> Script
    }
    def classLoaderId = ClassLoaderIds.buildScript("foo", "bar")

    def setup() {
        Resource resource = Mock()
        _ * source.resource >> resource
        _ * resource.text >> 'this is the script'
        _ * source.className >> 'ScriptClassName'
        _ * source.fileName >> 'ScriptFileName'
        _ * operation.id >> 'TransformerId'
        _ * operation.transformer >> transformer
        _ * cache.baseDir >> cacheDir
        _ * validator.isValid() >> true
    }

    def "loads classes from cache directory"() {
        when:
        def result = compiler.compile(source, classLoader, classLoaderId, operation, Script, verifier).loadClass()

        then:
        result == Script
        1 * cacheRepository.cache("scripts/ScriptClassName/TransformerId") >> cacheBuilder
        1 * cacheBuilder.withProperties(!null) >> { args ->
            assert args[0].get('source.filename') == 'ScriptFileName'
            assert args[0].containsKey('source.hash')
            return cacheBuilder
        }
        1 * cacheBuilder.withInitializer(!null) >> cacheBuilder
        1 * cacheBuilder.withDisplayName(!null) >> cacheBuilder
        1 * cacheBuilder.withValidator(!null) >> cacheBuilder
        1 * cacheBuilder.open() >> cache
        1 * scriptCompilationHandler.loadFromDir(source, classLoader, classesDir, metadataDir, operation, Script, classLoaderId) >> compiledScript
        0 * scriptCompilationHandler._
    }

    def "passes CacheValidator to cacheBuilder"() {
        setup:
        cacheRepository.cache("scripts/ScriptClassName/TransformerId") >> cacheBuilder
        cacheBuilder.withProperties(!null) >> cacheBuilder
        cacheBuilder.withInitializer(!null) >> cacheBuilder
        cacheBuilder.withDisplayName(!null) >> cacheBuilder
        cacheBuilder.open() >> cache
        scriptCompilationHandler.loadFromDir(source, classLoader, classesDir, metadataDir, operation, Script, classLoaderId) >> compiledScript

        when:
        compiler.compile(source, classLoader, classLoaderId, operation, Script, verifier)

        then:
        1 * cacheBuilder.withValidator(validator) >> cacheBuilder
    }

    def "compiles classes to cache directory when cache is invalid"() {
        def initializer
        def classesDir = classesDir
        def metadataDir = new File(cacheDir, "metadata")

        when:
        def result = compiler.compile(source, classLoader, classLoaderId, operation, Script, verifier).loadClass()

        then:
        result == Script
        1 * cacheRepository.cache("scripts/ScriptClassName/TransformerId") >> cacheBuilder
        1 * cacheBuilder.withProperties(!null) >> cacheBuilder
        1 * cacheBuilder.withDisplayName(!null) >> cacheBuilder
        1 * cacheBuilder.withValidator(!null) >> cacheBuilder
        1 * cacheBuilder.withInitializer(!null) >> { args -> initializer = args[0]; return cacheBuilder }
        1 * cacheBuilder.open() >> { initializer.execute(cache); return cache }
        1 * scriptCompilationHandler.compileToDir(source, classLoader, classesDir, metadataDir, operation, Script, verifier)
        1 * scriptCompilationHandler.loadFromDir(source, classLoader, classesDir, metadataDir, operation, Script, classLoaderId) >> compiledScript
        0 * scriptCompilationHandler._
    }

    def "reports compilation progress even in case of a failure"() {
        def factory = Mock(ProgressLoggerFactory)
        def delegate = Mock(Action)
        def cache = Mock(PersistentCache)
        def logger = Mock(ProgressLogger)

        def initializer = new FileCacheBackedScriptClassCompiler.ProgressReportingInitializer(factory, delegate)

        when:
        initializer.execute(cache)

        then:
        def ex = thrown(RuntimeException)
        ex.message == "Boo!"

        1 * factory.newOperation(FileCacheBackedScriptClassCompiler) >> logger
        1 * logger.start("Compile script into cache", "Compiling script into cache") >> logger

        then:
        1 * delegate.execute(cache) >> { throw new RuntimeException("Boo!") } //stress it a bit with a failure

        then:
        1 * logger.completed()
    }
}
