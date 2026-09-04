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

import org.gradle.api.internal.provider.PropertyInternal;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.jspecify.annotations.Nullable;

/**
 * Runtime target for instrumented {@link Property#set} and {@link Property#convention} call sites.
 */
public final class PropertyCallSites {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private PropertyCallSites() {
    }

    public static void set(Property<Object> property, @Nullable Object value, String callSite) {
        if (!capturesLocations(property)) {
            property.set(value);
            return;
        }
        runAt(callSite, () -> property.set(value));
    }

    public static void set(Property<Object> property, Provider<Object> value, String callSite) {
        if (!capturesLocations(property)) {
            property.set(value);
            return;
        }
        runAt(callSite, () -> property.set(value));
    }

    public static Property<Object> convention(Property<Object> property, @Nullable Object value, String callSite) {
        if (!capturesLocations(property)) {
            return property.convention(value);
        }
        String previous = CURRENT.get();
        CURRENT.set(callSite);
        try {
            return property.convention(value);
        } finally {
            restore(previous);
        }
    }

    public static Property<Object> convention(Property<Object> property, Provider<Object> value, String callSite) {
        if (!capturesLocations(property)) {
            return property.convention(value);
        }
        String previous = CURRENT.get();
        CURRENT.set(callSite);
        try {
            return property.convention(value);
        } finally {
            restore(previous);
        }
    }

    public static @Nullable String current() {
        return CURRENT.get();
    }

    private static boolean capturesLocations(Property<?> property) {
        return property instanceof PropertyInternal<?> && ((PropertyInternal<?>) property).capturesPropertyCallSites();
    }

    private static void runAt(String callSite, Runnable operation) {
        String previous = CURRENT.get();
        CURRENT.set(callSite);
        try {
            operation.run();
        } finally {
            restore(previous);
        }
    }

    private static void restore(@Nullable String previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
