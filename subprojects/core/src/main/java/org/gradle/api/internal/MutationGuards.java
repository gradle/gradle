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

package org.gradle.api.internal;

import org.gradle.api.Action;

public class MutationGuards {
    private static final MutationGuard IDENTITY_MUTATION_GUARD = new MutationGuard() {
        @Override
        public <T> Action<? super T> wrapLazyAction(Action<? super T> action) {
            return action;
        }

        @Override
        public <T> Action<? super T> wrapEagerAction(Action<? super T> action) {
            return action;
        }

        @Override
        public boolean isLazyContext() {
            return false;
        }

        @Override
        public void assertEagerContext(String methodName, Object target) {
            // do nothing
        }

        @Override
        public <T> void assertEagerContext(String methodName, T target, Class<T> targetType) {
            // do nothing
        }
    };

    /**
     * Returns the identity mutation guard. It protect and do nothing.
     */
    public static MutationGuard identity() {
        return IDENTITY_MUTATION_GUARD;
    }
}
