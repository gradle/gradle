/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal;

import org.gradle.api.Action;

public abstract class BiActions {

    private static final BiAction<Object, Object> NOOP = new BiAction<Object, Object>() {
        @Override
        public void execute(Object o, Object o2) {

        }
    };

    private BiActions() {
    }

    public static BiAction<Object, Object> doNothing() {
        return NOOP;
    }

    public static <A, B> BiAction<A, B> composite(final BiAction<? super A, ? super B>... actions) {
        return new BiAction<A, B>() {
            @Override
            public void execute(A a, B b) {
                for (BiAction<? super A, ? super B> action : actions) {
                    action.execute(a, b);
                }
            }
        };
    }

    public static <A> BiAction<A, Object> usingFirstArgument(final Action<? super A> action) {
        return new BiAction<A, Object>() {
            @Override
            public void execute(A a, Object o) {
                action.execute(a);
            }
        };
    }
}
