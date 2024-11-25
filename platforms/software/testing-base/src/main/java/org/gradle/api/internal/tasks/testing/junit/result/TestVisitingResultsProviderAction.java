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

import org.gradle.api.Action;

/**
 * Specialization of {@link org.gradle.api.Action} for visiting leaf nodes in a test tree.
 *
 * <p>
 * This is used by legacy code that did not have the ability to consume anything but a class and a method as test tree levels.
 * The class results are visited directly by {@link TestResultsProvider#visitChildren(Action)} on the root, and then "methods" or "tests" are visited
 * by calling {@link TestResultsProvider#visitChildren(Action)} on each of those providers with a subclass of this action.
 * </p>
 */
public abstract class TestVisitingResultsProviderAction implements Action<TestResultsProvider> {
    @Override
    public void execute(TestResultsProvider provider) {
        if (provider.hasChildren()) {
            provider.visitChildren(this);
        } else {
            visitTest(provider);
        }
    }

    protected abstract void visitTest(TestResultsProvider provider);
}
