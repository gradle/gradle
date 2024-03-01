/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.internal.graph;

import java.util.Collection;

/**
 * A directed graph with nodes of type N. Each node has a collection of values of type V.
 */
public interface DirectedGraph<N, V> {
    void getNodeValues(N node, Collection<? super V> values, Collection<? super N> connectedNodes);
}
