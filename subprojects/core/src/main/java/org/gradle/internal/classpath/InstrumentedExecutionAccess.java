/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath;

import org.gradle.api.NonNullApi;

import java.util.concurrent.atomic.AtomicReference;

/**
 * As {@link Instrumented} is using to augment calls at configuration time, this class is using
 * to augment calls at execution time.
 */
@NonNullApi
public class InstrumentedExecutionAccess {

    private static final Listener NO_OP = new Listener() {

        @Override
        public void disallowedAtExecutionInjectedServiceAccessed(Class<?> injectedServiceType, String propertyName, String consumer) {
        }
    };

    private static final AtomicReference<Listener> LISTENER = new AtomicReference<>(NO_OP);

    private static Listener listener() {
        return LISTENER.get();
    }

    public static void setListener(Listener listener) {
        LISTENER.set(listener);
    }

    public static void discardListener() {
        LISTENER.set(NO_OP);
    }

    /**
     * Called by generated code
     *
     * Invoked when the code accesses the injected service of type that disallowed at execution time (eg. Project)
     *
     * @param injectedServiceType the type of the injected service that was accessed
     * @param consumer class name of consumer that contains the code accesses the injected service
     */
    public static void disallowedAtExecutionInjectedServiceAccessed(
        Class<?> injectedServiceType,
        String getterName,
        String consumer
    ) {
        listener().disallowedAtExecutionInjectedServiceAccessed(injectedServiceType, getterName, consumer);
    }

    @NonNullApi
    public interface Listener {
        void disallowedAtExecutionInjectedServiceAccessed(Class<?> injectedServiceType, String propertyName, String consumer);
    }
}
