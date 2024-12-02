/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit.result;

import java.util.List;

/**
 * A tree of {@link PersistentTestResult}, which also contains the id for matching with {@link TestOutputStore}.
 */
public class PersistentTestResultTree {
    private final long id;
    private final PersistentTestResult result;
    private final List<PersistentTestResultTree> children;

    public PersistentTestResultTree(long id, PersistentTestResult result, List<PersistentTestResultTree> children) {
        this.id = id;
        this.result = result;
        this.children = children;
    }

    public long getId() {
        return id;
    }

    public PersistentTestResult getResult() {
        return result;
    }

    public List<PersistentTestResultTree> getChildren() {
        return children;
    }
}
