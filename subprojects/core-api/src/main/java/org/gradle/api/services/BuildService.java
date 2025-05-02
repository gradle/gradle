/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.services;

import org.gradle.api.Action;

import javax.inject.Inject;

/**
 * Represents a user provided "service" that is used by a Gradle build. Often these services are shared by multiple tasks and hold
 * some state that tasks use to do their work. A service implementation might hold, for example, an in-memory cache that tasks use
 * to improve performance. Or, as another example, a service implementation might represent a web service that the build starts and
 * uses.
 *
 * <p>To create a service, create an abstract subclass of this interface and use {@link BuildServiceRegistry#registerIfAbsent(String, Class, Action)}
 * to register one or more instances. This method returns a {@link org.gradle.api.provider.Provider} that you can use to connect
 * the service to tasks.</p>
 *
 * <p>A service implementation may optionally take parameters. To do this create a subtype of {@link BuildServiceParameters} and declare this
 * type as the type parameter to the service implementation.</p>
 *
 * <p>A service may optionally implement {@link AutoCloseable}, in which case {@link AutoCloseable#close()} will be called when
 * the service instance is no longer required. The implementation can release any resources it may be holding open.
 *
 * <p>It is important to note that service implementations must be thread-safe, as they may be used by multiple tasks concurrently.</p>
 *
 * @param <T> The type of parameters used by the service.
 * @since 6.1
 */
public interface BuildService<T extends BuildServiceParameters> {
    /**
     * Returns the parameters of this service. You should not implement this method, but instead leave it abstract.
     */
    @Inject
    T getParameters();
}
