/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.caching.fixtures

import org.gradle.integtests.fixtures.executer.ExecutionResult


internal
fun ExecutionResult.classLoadingCache(action: ClassLoadingCache.() -> Unit) =
    action(ClassLoadingCache(this))


internal
class ClassLoadingCache(private val result: ExecutionResult) : KotlinDslCacheFixture {

    override fun misses(vararg cachedScripts: CachedScript) =
        cachedScripts.forEach { assertClassLoads(it, 1) }

    override fun hits(vararg cachedScripts: CachedScript) =
        cachedScripts.forEach { assertClassLoads(it, 0) }

    private
    fun assertClassLoads(cachedScript: CachedScript, count: Int) =
        when (cachedScript) {
            is CachedScript.WholeFile -> cachedScript.stages.forEach { assertClassLoads(it, count) }
            is CachedScript.CompilationStage -> assertClassLoads(cachedScript, count)
        }

    private
    fun assertClassLoads(stage: CachedScript.CompilationStage, count: Int) =
        result.assertOccurrenceCountOf("loading", stage, count)
}
