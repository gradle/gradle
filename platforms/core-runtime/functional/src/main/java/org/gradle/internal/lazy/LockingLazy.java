/*
 * Copyright 2003 the original author or authors.
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
package org.gradle.internal.lazy;

import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.ThreadSafe;
import java.util.function.Supplier;

/**
 * This is basically the same thing as Guava's NonSerializableMemoizingSupplier
 */
@ThreadSafe
class LockingLazy<T extends @Nullable Object> implements Lazy<T> {
    private volatile @Nullable Supplier<T> supplier;
    private volatile boolean initialized;
    // "value" does not need to be volatile;
    // visibility piggybacks on volatile read of "initialized".
    private @Nullable T value;

    public LockingLazy(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    // NullAway cannot infer the invariants here:
    // !initialized => (supplier != null && value == null)
    // initialized => (supplier == null && value != null)
    @SuppressWarnings({"NullAway", "DataFlowIssue"})
    @Override
    public T get() {
        // A 2-field variant of Double-Checked Locking.
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    // `supplier` cannot be null here
                    T t = supplier.get();
                    value = t;
                    initialized = true;
                    // Release the delegate to GC.
                    supplier = null;
                    return t;
                }
            }
        }
        // `value` cannot be null here.
        return value;
    }
}
