/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling;

/**
 * A handler for an asynchronous operation which returns an object of type T.
 *
 * @param <T> The result type.
 * @since 1.0-milestone-3
 */
public interface ResultHandler<T> {

    /**
     * Handles successful completion of the operation.
     *
     * @param result the result
     * @since 1.0-milestone-3
     */
    void onComplete(T result);

    /**
     * Handles a failed operation. This method is invoked once only for a given operation.
     *
     * @param failure the failure
     * @since 1.0-milestone-3
     */
    void onFailure(GradleConnectionException failure);
}
