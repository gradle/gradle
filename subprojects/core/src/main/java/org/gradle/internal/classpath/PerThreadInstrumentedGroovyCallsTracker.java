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
import java.util.function.Supplier;

@NonNullApi
public class PerThreadInstrumentedGroovyCallsTracker implements InstrumentedGroovyCallsTracker {
    private final ThreadLocal<InstrumentedGroovyCallsTracker> perThreadImplementation;

    public PerThreadInstrumentedGroovyCallsTracker(Supplier<InstrumentedGroovyCallsTracker> implementationSupplier) {
        perThreadImplementation = ThreadLocal.withInitial(implementationSupplier);
    }

    @Override
    public EntryPointCallSite enterCall(String callerClassName, String callableName, CallKind callKind) {
        return perThreadImplementation.get().enterCall(callerClassName, callableName, callKind);
    }

    @Override
    public void leaveCall(EntryPointCallSite entryPoint) {
        perThreadImplementation.get().leaveCall(entryPoint);
    }

    @Nullable
    @Override
    public String findCallerForCurrentCallIfNotIntercepted(String callableName, CallKind kind) {
        return perThreadImplementation.get().findCallerForCurrentCallIfNotIntercepted(callableName, kind);
    }

    @Override
    public void markCurrentCallAsIntercepted(String callableName, CallKind kind) {
        perThreadImplementation.get().markCurrentCallAsIntercepted(callableName, kind);
    }
}
