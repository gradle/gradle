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

package org.gradle.kotlin.dsl.resolver

import org.gradle.kotlin.dsl.concurrent.ResurrectingThread
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel

import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine


private
typealias AsyncModelRequest = Pair<KotlinBuildScriptModelRequest, Continuation<KotlinBuildScriptModel?>>


internal
open class KotlinBuildScriptModelRepository {

    open suspend fun scriptModelFor(request: KotlinBuildScriptModelRequest): KotlinBuildScriptModel? =
        suspendCoroutine { k ->
            accept(request, k)
            processor.poke()
        }

    protected
    open fun accept(request: KotlinBuildScriptModelRequest, k: Continuation<KotlinBuildScriptModel?>) {
        q.push(request to k)
    }

    protected
    open fun fetch(request: KotlinBuildScriptModelRequest): KotlinBuildScriptModel =
        fetchKotlinBuildScriptModelFor(request)

    private
    val q = ConcurrentGroupingQueue<AsyncModelRequest> {
        first.scriptFile == it.first.scriptFile && first.projectDir == it.first.projectDir
    }

    private
    val processor = ResurrectingThread("Kotlin DSL Resolver") {
        while (true) {
            val group = q.nextGroup()
            if (group.isEmpty()) {
                break
            }
            process(group)
        }
    }

    private
    fun process(group: List<AsyncModelRequest>) {
        val (mostRecentRequest, k) = group.first()
        val result = runCatching { fetch(mostRecentRequest) }
        resumeAll(supersededRequestsFrom(group), Result.success(null))
        resume(k, result)
    }

    private
    fun resumeAll(ks: Sequence<Continuation<KotlinBuildScriptModel?>>, result: Result<KotlinBuildScriptModel?>) {
        for (k in ks) {
            resume(k, result)
        }
    }

    private
    fun supersededRequestsFrom(group: List<AsyncModelRequest>) =
        group.asReversed().asSequence().take(group.size - 1).map { (_, k) -> k }

    private
    fun resume(k: Continuation<KotlinBuildScriptModel?>, result: Result<KotlinBuildScriptModel?>) {
        ignoringErrors { k.resumeWith(result) }
    }

    private
    inline fun ignoringErrors(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
