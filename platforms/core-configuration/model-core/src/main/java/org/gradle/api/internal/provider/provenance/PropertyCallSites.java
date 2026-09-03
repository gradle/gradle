/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.provider.provenance;

import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.jspecify.annotations.Nullable;

/**
 * Runtime target of the property mutation call site instrumentation.
 * <p>
 * Instrumented build logic calls these instead of {@code Property.set}, passing the source position that was a
 * constant at the call site. The mutation is performed inside, so the position is published for exactly the
 * duration of that mutation and cannot leak to an unrelated one: a stale position read by some later mutation
 * would be worse than no position at all.
 * <p>
 * This is called from rewritten bytecode, so the method names and descriptors are part of the contract with
 * {@code PropertyMutationInterceptor}.
 */
public class PropertyCallSites {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private static volatile boolean enabled;

    private PropertyCallSites() {
    }

    /**
     * Switched on whenever provenance is enabled. Publishing an instrumented call site is cheap enough to need
     * no separate switch: the position is a constant in the caller's constant pool, so this costs a thread
     * local write and its restore. While off, an instrumented call is an ordinary {@code set} plus one boolean
     * read.
     */
    public static void setEnabled(boolean enabled) {
        PropertyCallSites.enabled = enabled;
    }

    public static void set(Property<Object> property, @Nullable Object value, String callSite) {
        if (!enabled) {
            property.set(value);
            return;
        }
        String previous = CURRENT.get();
        CURRENT.set(callSite);
        try {
            property.set(value);
        } finally {
            restore(previous);
        }
    }

    public static void set(Property<Object> property, @Nullable Provider<Object> value, String callSite) {
        if (!enabled) {
            property.set(value);
            return;
        }
        String previous = CURRENT.get();
        CURRENT.set(callSite);
        try {
            property.set(value);
        } finally {
            restore(previous);
        }
    }

    /**
     * The call site of the mutation currently being performed, if it came from instrumented code.
     */
    @Nullable
    public static String current() {
        return enabled ? CURRENT.get() : null;
    }

    private static void restore(@Nullable String previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
