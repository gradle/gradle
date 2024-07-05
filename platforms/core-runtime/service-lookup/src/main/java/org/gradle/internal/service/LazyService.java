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

/**
 * A lazy service can be consumed as a dependency
 * to avoid instantiation at the injection time.
 */
public interface LazyService<T> {

    /**
     * Returns the instance of the service.
     * <p>
     * The service will be created lazily on the first invocation.
     * All subsequent calls will return the same instance of the service.
     * <p>
     * Calling this method is <em>thread-safe</em>.
     *
     * @return service instance
     */
    T getInstance();

}
