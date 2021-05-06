/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.util.internal;

/**
 * Visits a tree with nodes of type T.
 */
public class TreeVisitor<T> {
    /**
     * Visits a node of the tree.
     */
    public void node(T node) {
    }

    /**
     * Starts visiting the children of the most recently visited node.
     */
    public void startChildren() {
    }

    /**
     * Finishes visiting the children of the most recently started node.
     */
    public void endChildren() {
    }
}
