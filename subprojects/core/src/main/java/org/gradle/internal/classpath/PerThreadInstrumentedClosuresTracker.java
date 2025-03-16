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

import org.jspecify.annotations.NullMarked;

import java.util.function.Supplier;

@NullMarked
public class PerThreadInstrumentedClosuresTracker implements InstrumentedClosuresTracker {
    private final ThreadLocal<InstrumentedClosuresTracker> perThreadTracker;

    public PerThreadInstrumentedClosuresTracker(Supplier<InstrumentedClosuresTracker> trackerForThread) {
        perThreadTracker = ThreadLocal.withInitial(trackerForThread);
    }

    @Override
    public void enterClosure(InstrumentableClosure thisClosure) {
        perThreadTracker.get().enterClosure(thisClosure);
    }

    @Override
    public void leaveClosure(InstrumentableClosure thisClosure) {
        perThreadTracker.get().leaveClosure(thisClosure);
    }

    @Override
    public void hitInstrumentedDynamicCall() {
        perThreadTracker.get().hitInstrumentedDynamicCall();
    }
}
