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
 * Code for extracting certain legacy patterns from the arbitrary nesting test results model.
 */
public class LegacyResultsHelper {
    public static void visitClasses(TestResultsProvider rootProvider, Action<? super TestResultsProvider> action) {
        rootProvider.visitChildren(new RecursiveClassFindingVisitor(action));
    }

    private static class RecursiveClassFindingVisitor implements Action<TestResultsProvider> {
        private final Action<? super TestResultsProvider> action;

        public RecursiveClassFindingVisitor(Action<? super TestResultsProvider> action) {
            this.action = action;
        }

        @Override
        public void execute(TestResultsProvider provider) {
            // Skip all providers without children, as we shouldn't report empty classes.
            if (!provider.hasChildren()) {
                return;
            }
            PersistentTestResult.LegacyProperties legacyProperties = provider.getResult().getLegacyProperties();
            if (legacyProperties != null && legacyProperties.isClass()) {
                action.execute(provider);
                // We don't go any deeper as legacy code only went one level deep. There shouldn't be any classes below other classes anyways.
            } else {
                provider.visitChildren(this);
            }
        }
    }
}
