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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

import org.gradle.kotlin.dsl.tooling.models.EditorReport
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel

import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.File

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

import kotlin.coroutines.Continuation


class KotlinBuildScriptModelRepositoryTest {

    @Test
    fun `cancels older requests before returning response to most recent request`() = runBlockingWithTimeout {

        // given:
        // Notice the project directory is never written to by the test so its path is immaterial
        val projectDir = File("/project-dir")
        val scriptFile = File(projectDir, "build.gradle.kts")

        /**
         * Request accepted into the queue.
         */
        val acceptedRequest = Channel<KotlinBuildScriptModelRequest>(3)

        /**
         * Request taken off the queue awaiting for a response.
         */
        val pendingRequest = Channel<KotlinBuildScriptModelRequest>(3)

        /**
         * Next response to [KotlinBuildScriptModelRepository.fetch].
         */
        val nextResponse = ArrayBlockingQueue<KotlinBuildScriptModel>(3)

        val subject = object : KotlinBuildScriptModelRepository() {

            override fun accept(request: KotlinBuildScriptModelRequest, k: Continuation<KotlinBuildScriptModel?>) {
                super.accept(request, k)
                acceptedRequest.trySendBlocking(request).getOrThrow()
            }

            override fun fetch(request: KotlinBuildScriptModelRequest): KotlinBuildScriptModel {
                pendingRequest.trySendBlocking(request).getOrThrow()
                return nextResponse.poll(defaultTestTimeoutMillis, TimeUnit.MILLISECONDS)!!
            }
        }

        fun newModelRequest() = KotlinBuildScriptModelRequest(projectDir, scriptFile)

        fun asyncResponseTo(request: KotlinBuildScriptModelRequest): Deferred<KotlinBuildScriptModel?> = async {
            subject.scriptModelFor(request)
        }

        // when:
        val pendingTasks =
            (0..2).map { index ->
                val request = newModelRequest()
                request to asyncResponseTo(request).also {
                    assertThat(
                        "Request is accepted",
                        acceptedRequest.receive(),
                        sameInstance(request)
                    )
                    if (index == 0) {
                        // wait for the first request to be taken off the queue
                        // so the remaining requests are received while the request handler is busy
                        assertThat(
                            pendingRequest.receive(),
                            sameInstance(request)
                        )
                    }
                }
            }

        // submit the first response
        // this response should satisfy the very first request
        val resp1 = newModelResponse().also(nextResponse::put)
        val (_, asyncResp1) = pendingTasks[0]
        assertThat(
            asyncResp1.await(),
            sameInstance(resp1)
        )

        // now we expect to receive the most recent request since the last request that got a response,
        // that's the last request in our list of pending tasks
        val (reqLast, asyncRespLast) = pendingTasks.last()
        assertThat(
            pendingRequest.receive(),
            sameInstance(reqLast)
        )

        // submit the second (and last) response
        val respLast = newModelResponse().also(nextResponse::put)

        // upon receiving this response, the request handler will first complete
        // any and all outdated requests off the queue by responding with `null`
        val (_, asyncResp2) = pendingTasks[1]
        assertThat(
            asyncResp2.await(),
            nullValue()
        )

        // only then the most recent request will be given its response
        assertThat(
            asyncRespLast.await(),
            sameInstance(respLast)
        )

        // and no other requests are left to process
        assertThat(
            withTimeoutOrNull(50) { pendingRequest.receive() },
            nullValue()
        )
    }

    private
    fun newModelResponse(): KotlinBuildScriptModel = StandardKotlinBuildScriptModel()

    data class StandardKotlinBuildScriptModel(
        private val classPath: List<File> = emptyList(),
        private val sourcePath: List<File> = emptyList(),
        private val implicitImports: List<String> = emptyList(),
        private val editorReports: List<EditorReport> = emptyList(),
        private val exceptions: List<String> = emptyList(),
        private val enclosingScriptProjectDir: File? = null
    ) : KotlinBuildScriptModel {
        override fun getClassPath() = classPath
        override fun getSourcePath() = sourcePath
        override fun getImplicitImports() = implicitImports
        override fun getEditorReports() = editorReports
        override fun getExceptions() = exceptions
        override fun getEnclosingScriptProjectDir() = enclosingScriptProjectDir
    }
}


internal
fun runBlockingWithTimeout(timeMillis: Long = defaultTestTimeoutMillis, block: suspend CoroutineScope.() -> Unit) {
    runBlocking {
        withTimeout(timeMillis, block)
    }
}


internal
const val defaultTestTimeoutMillis = 5000L
