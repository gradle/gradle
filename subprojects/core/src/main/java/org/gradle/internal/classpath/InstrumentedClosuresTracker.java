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

/**
 * Tracks the closures that are currently present in the call stack.
 * An instrumented closure must invoke {@link InstrumentedClosuresTracker#enterClosure} when it starts executing and {@link InstrumentedClosuresTracker#leaveClosure}
 * right before it leaves the call stack. <p>
 *
 * {@link InstrumentedClosuresTracker#hitInstrumentedDynamicCall} is invoked by the instrumentation infrastructure on every invocation that is dynamically dispatched
 * to the current closures chain and can potentially be intercepted. The implementation must ensure that all the closures in the scope are processed in a way that
 * ensures call interception if a call is dispatched to them.
 */
@NonNullApi
public interface InstrumentedClosuresTracker {
    void enterClosure(InstrumentableClosure thisClosure);
    void leaveClosure(InstrumentableClosure thisClosure);
    void hitInstrumentedDynamicCall();
}
