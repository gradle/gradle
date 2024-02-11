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

import javax.annotation.Nullable;

@NonNullApi
public class InstrumentedGroovyCallsHelper {
    /**
     * Executes the given {@code callable} in the context of an entry point produced from entering a dynamically dispatched
     * call described by the {@code callableName} and {@code kind}
     * from a class identified by {@code consumerClass}.
     * After running the callable, ensures that the entry point leaves the context and returns the callable's result.
     */
    public static <T> @Nullable T withEntryPoint(String consumerClass, String callableName, InstrumentedGroovyCallsTracker.CallKind kind, ThrowingCallable<T> callable) throws Throwable {
        InstrumentedGroovyCallsTracker.EntryPointCallSite entryPoint = INSTANCE.enterCall(consumerClass, callableName, kind);
        try {
            return callable.call();
        } finally {
            INSTANCE.leaveCall(entryPoint);
        }
    }

    @NonNullApi
    public interface ThrowingCallable<T> {
        @Nullable
        T call() throws Throwable;
    }

    static final InstrumentedGroovyCallsTracker INSTANCE = new PerThreadInstrumentedGroovyCallsTracker(DefaultInstrumentedGroovyCallsTracker::new);
}
