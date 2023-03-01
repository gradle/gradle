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

package org.gradle.kotlin.dsl.concurrent

import org.awaitility.Duration.ONE_SECOND
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilNotNull

import org.gradle.kotlin.dsl.support.useToRun

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.IOException

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


class DefaultAsyncIOScopeFactoryTest {

    @Test
    fun `#io failure is reported upon IOScope#close`() {

        withAsyncIOScopeFactory {

            val failure = IOException()

            val scope = newScope().apply {
                io { throw failure }
            }

            assertThat(
                exceptionOrNullFrom { scope.close() },
                sameInstance<Throwable>(failure)
            )
        }
    }

    @Test
    fun `#io failure is reported upon next #io call`() {

        withAsyncIOScopeFactory {

            val expectedFailure = IOException()

            val scope = newScope().apply {
                io { throw expectedFailure }
            }

            assertThat(
                awaitExceptionFrom(scope),
                sameInstance<Throwable>(expectedFailure)
            )
        }
    }

    @Test
    fun `IOScope failures are isolated`() {

        withAsyncIOScopeFactory {

            // given: a failure in one scope
            val failedScope = newScope().apply {
                io { throw IllegalStateException() }
            }
            awaitExceptionFrom(failedScope)

            // then: actions can still be scheduled in separate scope
            val isolatedScopeAction = CompletableFuture<Unit>()
            newScope().apply {
                io { isolatedScopeAction.complete(Unit) }
            }
            assertThat(
                isolatedScopeAction.get(1, TimeUnit.SECONDS),
                equalTo(Unit)
            )
        }
    }

    @Test
    fun `#io timeout reported upon IOScope#close`() {
        val settings = object : AsyncIOScopeSettings {
            override val ioActionTimeoutMs = 1L
        }
        withAsyncIOScopeFactory(settings) {
            val scope = newScope().apply {
                io { Thread.sleep(100L) }
            }
            assertThat(
                exceptionOrNullFrom { scope.close() },
                instanceOf(TimeoutException::class.java)
            )
        }
    }

    private
    fun awaitExceptionFrom(scope: IOScope) =
        await atMost ONE_SECOND untilNotNull { exceptionOrNullFrom { scope.io { } } }

    private
    fun exceptionOrNullFrom(action: () -> Unit) = runCatching(action).exceptionOrNull()

    private
    fun withAsyncIOScopeFactory(
        settings: AsyncIOScopeSettings = JavaSystemPropertiesAsyncIOScopeSettings(),
        action: DefaultAsyncIOScopeFactory.() -> Unit
    ) {
        DefaultAsyncIOScopeFactory(settings) { Executors.newSingleThreadExecutor() }.useToRun(action)
    }
}
