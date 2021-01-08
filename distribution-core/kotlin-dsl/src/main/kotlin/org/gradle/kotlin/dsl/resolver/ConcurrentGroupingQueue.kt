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

import org.gradle.kotlin.dsl.concurrent.pollTimeoutMillis

import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

import kotlin.concurrent.withLock


/**
 * A queue that gives priority to the most recently pushed element.
 */
internal
class ConcurrentGroupingQueue<T>(

    /**
     * Predicate to check whether the given most recently pushed element (the receiver)
     * supersedes the given less recent element (the argument).
     *
     * When the predicate returns true the argument is considered to be in the same group
     * as the receiver.
     */
    private
    val supersedes: T.(T) -> Boolean

) {

    private
    val q = ArrayDeque<T>()

    private
    val lock = ReentrantLock()

    private
    val notEmpty = lock.newCondition()

    fun push(element: T) {
        lock.withLock {
            q.addFirst(element)
            if (q.size == 1) {
                notEmpty.signal()
            }
        }
    }

    /**
     * Returns the next group of elements after removing them from the queue.
     *
     * The group contains the most recently pushed element plus all
     * elements superseded by it ordered from most recent to least recent.
     */
    fun nextGroup(timeoutMillis: Long = pollTimeoutMillis): List<T> {
        lock.withLock {
            if (q.isNotEmpty()) {
                return takeNextGroup()
            }
            if (notEmpty.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                return takeNextGroup()
            }
        }
        return emptyList()
    }

    private
    fun takeNextGroup(): List<T> {
        require(q.isNotEmpty())
        val group = mutableListOf<T>()
        q.iterator().run {
            // the most recently pushed element
            val mostRecent = next()
            group.add(mostRecent)
            remove()
            // plus all elements superseded by it
            for (next in this) {
                if (mostRecent.supersedes(next)) {
                    group.add(next)
                    remove()
                }
            }
        }
        return group
    }
}
