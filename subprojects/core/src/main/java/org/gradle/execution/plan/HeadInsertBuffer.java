/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.execution.plan;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Collects elements to be inserted at the head of a {@link Deque}, preserving the order in which they were added.
 *
 * <p>
 * That is, if elements are added as [A, B, C], then when they are added to a {@code Deque} of [D], the resulting {@code Deque} will be [A, B, C, D].
 * </p>
 *
 * <p>Instances are intended to be reused across iterations of a traversal to save memory by reusing the allocated buffer.</p>
 */
class HeadInsertBuffer<T> {
    private final List<T> buffer = new ArrayList<>();

    /**
     * Adds an element to the buffer.
     *
     * @param element the element to add
     */
    public void add(T element) {
        buffer.add(element);
    }

    /**
     * Drains the buffer to the given {@link Deque}, adding the elements in the order they were added to the buffer.
     *
     * @param queue the {@link Deque} to drain the buffer to
     */
    public void drainTo(Deque<? super T> queue) {
        for (T element : Lists.reverse(buffer)) {
            queue.addFirst(element);
        }
        buffer.clear();
    }
}
