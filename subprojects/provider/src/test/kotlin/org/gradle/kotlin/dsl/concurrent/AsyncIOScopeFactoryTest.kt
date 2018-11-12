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

import org.gradle.kotlin.dsl.support.useToRun

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.IOException

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class AsyncIOScopeFactoryTest {

    @Test
    fun `#io failure is reported upon IOScope#close`() {

        withAsyncIOScopeFactory {

            val failure = IOException()

            val scope = newScope().apply {
                io { throw failure }
            }

            assertThat(
                runCatching { scope.close() }.exceptionOrNull(),
                sameInstance<Throwable>(failure)
            )
        }
    }

    @Test
    fun `#io failure is reported upon next #io call`() {

        withAsyncIOScopeFactory {

            val failure = IOException()

            val scope = newScope().apply {
                io { throw failure }
            }

            Thread.sleep(1)

            assertThat(
                runCatching { scope.io { } }.exceptionOrNull(),
                sameInstance<Throwable>(failure)
            )
        }
    }

    @Test
    fun `IOScope failures are isolated`() {

        withAsyncIOScopeFactory {

            newScope().apply {
                io { throw IllegalStateException() }
            }

            Thread.sleep(1)

            val scope2Action = CompletableFuture<Unit>()
            newScope().apply {
                io { scope2Action.complete(Unit) }
            }

            assertThat(
                scope2Action.get(50, TimeUnit.MILLISECONDS),
                equalTo(Unit)
            )
        }
    }

    private
    fun withAsyncIOScopeFactory(action: AsyncIOScopeFactory.() -> Unit) {
        AsyncIOScopeFactory { Executors.newSingleThreadExecutor() }.useToRun(action)
    }
}
