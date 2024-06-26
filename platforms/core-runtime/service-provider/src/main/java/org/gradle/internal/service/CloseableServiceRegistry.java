/*
 * Copyright 2024 the original author or authors.
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

import java.io.Closeable;

/**
 * Managed version of a {@link ServiceRegistry} that controls the lifetime of the registry members,
 * such as service instances and factories.
 * <p>
 * Members created by the registry are stopped and closed when this registry is {@link #close() closed}.
 */
public interface CloseableServiceRegistry extends ServiceRegistry, Closeable {

    /**
     * Closes this registry by stopping and closing all members managed by it.
     * <p>
     * If a member implements {@link java.io.Closeable#close() Closeable} or
     * {@link org.gradle.internal.concurrent.Stoppable#stop() Stoppable} then the appropriate
     * method is called to dispose of it.
     * <p>
     * Members are closed in reverse dependency order.
     *
     * @see CloseableServiceRegistry
     */
    @Override
    void close();
}
