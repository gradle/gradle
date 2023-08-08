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

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import kotlin.concurrent.thread


class ConcurrentGroupingQueueTest {

    @Test
    fun `groups similar requests ordered by most recent`() {

        val requestsInGroup1 = (0..1).map { Request(1, it) }
        val requestsInGroup2 = (0..1).map { Request(2, it) }

        val subject = ConcurrentGroupingQueue<Request> { group == it.group }
        for ((r1, r2) in requestsInGroup1 zip requestsInGroup2) {
            // interleave the requests
            subject.push(r1)
            subject.push(r2)
        }

        assertThat(
            subject.nextGroup(),
            equalTo(requestsInGroup2.reversed())
        )

        assertThat(
            subject.nextGroup(),
            equalTo(requestsInGroup1.reversed())
        )
    }

    @Test
    fun `#nextGroup honours timeout`() {

        val subject = ConcurrentGroupingQueue<Int> { false }

        val pushElementSignal = CountDownLatch(1)
        thread {
            pushElementSignal.await(defaultTestTimeoutMillis, TimeUnit.MILLISECONDS)
            subject.push(42)
        }
        assertThat(
            subject.nextGroup(50),
            equalTo(emptyList())
        )
        pushElementSignal.countDown()

        assertThat(
            subject.nextGroup(defaultTestTimeoutMillis),
            equalTo(listOf(42))
        )
    }

    private
    data class Request(val group: Int, val payload: Int)
}
