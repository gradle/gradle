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

package org.gradle.internal.scheduler;

import org.gradle.api.specs.Spec;

import java.io.Closeable;
import java.util.Collection;

public interface Scheduler extends Closeable {
    /**
     * Executes the given {@code entryNodes} in the {@code graph}, excluding nodes
     * that don't match the {@code filter}.
     */
    GraphExecutionResult execute(Graph graph, Collection<? extends Node> entryNodes, boolean continueOnFailure, Spec<? super Node> filter);

    @Override
    void close();
}
