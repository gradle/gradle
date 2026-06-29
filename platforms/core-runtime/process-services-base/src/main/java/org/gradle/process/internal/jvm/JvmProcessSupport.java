/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.process.internal.jvm;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Version-specific access to {@link Process} APIs or their polyfill.
 */
@NullMarked
public final class JvmProcessSupport {

    private static final AtomicReference<@Nullable Field> PID_FIELD = new AtomicReference<>();

    private static Field getPidField(Process process) {
        Field pidField = PID_FIELD.get();
        if (pidField == null) {
            try {
                // UNIXProcess / ProcessImpl have a 'pid' field.
                pidField = process.getClass().getDeclaredField("pid");
                pidField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException("Failed to determine process id", e);
            }
            // Canonicalize what field reference we use, and cache for all future calls.
            if (!PID_FIELD.compareAndSet(null, pidField)) {
                pidField = Objects.requireNonNull(PID_FIELD.get());
            }
        }
        return pidField;
    }

    /**
     * Returns the native process id of the given process.
     */
    public static long pid(Process process) {
        try {
            return ((Number) getPidField(process).get(process)).longValue();
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to determine process id", e);
        }
    }

    /**
     * Returns a future that completes with the process once it has exited.
     *
     * <p>The returned future completes on the supplied {@code executor}.
     */
    public static CompletableFuture<Process> onExit(Process process, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            // Mirroring Java 9+ onExit behavior, we ensure waitFor exits first,
            // then we re-interrupt the thread and return the process so the future completes.
            boolean interrupted = false;
            while (true) {
                try {
                    process.waitFor();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            return process;
        }, executor);
    }

    /**
     * Destroys all known descendants (children, grandchildren, ...) of the given process.
     *
     * <p>This is a no-op on Java 8, where the descendant API is not available.
     */
    @SuppressWarnings("unused")
    public static void destroyDescendants(Process process) {
    }

    private JvmProcessSupport() {
    }
}
