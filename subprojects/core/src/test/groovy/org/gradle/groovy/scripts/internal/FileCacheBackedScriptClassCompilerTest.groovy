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

import spock.lang.Specification
import org.gradle.cache.CacheRepository
import org.gradle.api.internal.resource.Resource
import org.gradle.cache.DirectoryCacheBuilder
import org.gradle.cache.PersistentCache
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.Transformer
import org.gradle.groovy.scripts.Script
import org.gradle.cache.CacheValidator

class FileCacheBackedScriptClassCompilerTest extends Specification {
    final ScriptCompilationHandler scriptCompilationHandler = Mock()
    final CacheRepository cacheRepository = Mock()
    final DirectoryCacheBuilder cacheBuilder = Mock()
    final CacheValidator validator = Mock()
    final PersistentCache cache = Mock()
    final ScriptSource source = Mock()
    final ClassLoader classLoader = Mock()
    final Transformer transformer = Mock()
    final File cacheDir = new File("base-dir")
    final FileCacheBackedScriptClassCompiler compiler = new FileCacheBackedScriptClassCompiler(cacheRepository, validator, scriptCompilationHandler)

    def setup() {
        Resource resource = Mock()
        _ * source.resource >> resource
        _ * resource.text >> 'this is the script'
        _ * source.className >> 'ScriptClassName'
        _ * source.fileName >> 'ScriptFileName'
        _ * transformer.id >> 'TransformerId'
        _ * cache.baseDir >> cacheDir
        _ * validator.isValid() >> true
    }

    def "loads classes from cache directory"() {
        when:
        def result = compiler.compile(source, classLoader, transformer, Script)

        then:
        result == Script
        1 * cacheRepository.cache("scripts/ScriptClassName/Script/TransformerId") >> cacheBuilder
        1 * cacheBuilder.withProperties(!null) >> { args ->
            assert args[0].get('source.filename') == 'ScriptFileName'
            assert args[0].containsKey('source.hash')
            return cacheBuilder
        }
        1 * cacheBuilder.withInitializer(!null) >> cacheBuilder
        1 * cacheBuilder.withDisplayName(!null) >> cacheBuilder
        1 * cacheBuilder.withValidator(!null) >> cacheBuilder
        1 * cacheBuilder.open() >> cache
        1 * scriptCompilationHandler.loadFromDir(source, classLoader, new File(cacheDir, "classes"), Script) >> Script
        0 * scriptCompilationHandler._
    }

    def "passes CacheValidator to cacheBuilder"() {
        setup:
        cacheRepository.cache("scripts/ScriptClassName/Script/TransformerId") >> cacheBuilder
        cacheBuilder.withProperties(!null) >> cacheBuilder
        cacheBuilder.withInitializer(!null) >> cacheBuilder
        cacheBuilder.withDisplayName(!null) >> cacheBuilder
        cacheBuilder.open() >> cache
        scriptCompilationHandler.loadFromDir(source, classLoader, new File(cacheDir, "classes"), Script) >> Script

        when:
        compiler.compile(source, classLoader, transformer, Script)

        then:
        1 * cacheBuilder.withValidator(validator) >> cacheBuilder


    }

    def "compiles classes to cache directory when cache is invalid"() {
        def initializer

        when:
        def result = compiler.compile(source, classLoader, transformer, Script)

        then:
        result == Script
        1 * cacheRepository.cache("scripts/ScriptClassName/Script/TransformerId") >> cacheBuilder
        1 * cacheBuilder.withProperties(!null) >> cacheBuilder
        1 * cacheBuilder.withDisplayName(!null) >> cacheBuilder
        1 * cacheBuilder.withValidator(!null) >> cacheBuilder
        1 * cacheBuilder.withInitializer(!null) >> {args -> initializer = args[0]; return cacheBuilder}
        1 * cacheBuilder.open() >> {initializer.execute(cache); return cache}
        1 * scriptCompilationHandler.compileToDir(source, classLoader, new File(cacheDir, "classes"), transformer, Script)
        1 * scriptCompilationHandler.loadFromDir(source, classLoader, new File(cacheDir, "classes"), Script) >> Script
        0 * scriptCompilationHandler._
    }
}
