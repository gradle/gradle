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

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit


internal
class EventLoop<T>(
    name: String = "Gradle Kotlin DSL Event Loop",
    val loop: (() -> T?) -> Unit
) {

    fun accept(event: T): Boolean {
        if (q.offer(event, offerTimeoutMillis, TimeUnit.MILLISECONDS)) {
            consumer.poke()
            return true
        }
        return false
    }

    private
    val q = ArrayBlockingQueue<T>(64)

    private
    var consumer = ResurrectingThread(name = name) {
        loop(::poll)
    }

    private
    fun poll(): T? = q.poll(pollTimeoutMillis, TimeUnit.MILLISECONDS)
}


private
const val offerTimeoutMillis = 50L


internal
const val pollTimeoutMillis = 5_000L
