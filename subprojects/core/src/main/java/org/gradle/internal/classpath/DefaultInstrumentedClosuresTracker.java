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

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.gradle.api.NonNullApi;

@NonNullApi
public class DefaultInstrumentedClosuresTracker implements InstrumentedClosuresTracker {
    /**
     * A multiset counting the entries in a closure, which is needed to correctly track reentrant recursive calls;
     */
    private final Object2IntMap<InstrumentableClosure> currentClosuresEntries = new Object2IntOpenHashMap<>();

    @Override
    public void enterClosure(InstrumentableClosure thisClosure) {
        currentClosuresEntries.mergeInt(thisClosure, 1, Integer::sum);
    }

    @Override
    public void leaveClosure(InstrumentableClosure thisClosure) {
        currentClosuresEntries.computeInt(thisClosure, (key, oldValue) -> {
            if (oldValue == null) {
                throw new IllegalStateException("leaveClosure called with an untracked instance");
            } else {
                if (oldValue == 1) {
                    return null;
                } else {
                    return oldValue - 1;
                }
            }
        });
    }

    @Override
    public void hitInstrumentedDynamicCall() {
        currentClosuresEntries.keySet().forEach(InstrumentableClosure::makeEffectivelyInstrumented);
    }
}
