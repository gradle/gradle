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

package org.gradle.internal.service;

/**
 * Receives notifications from a service registry when a service is looked up by a type that
 * is not annotated with {@link com.google.errorprone.annotations.ThreadSafe}.
 */
public interface UnsafeServiceAccessListener {

    /**
     * Invoked when {@code serviceType} is looked up through a registry configured with this
     * listener and the type is not marked thread-safe.
     */
    void onUnsafeAccess(Class<?> serviceType);
}
