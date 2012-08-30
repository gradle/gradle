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
     * Handles failures. A failure happens when the target Gradle version does not support
     * the features required to build this model. For example, when you have configured the long running operation with a settings
     *  like: {@link LongRunningOperation#setStandardInput(java.io.InputStream)}, {@link LongRunningOperation#setJavaHome(java.io.File)}, {@link LongRunningOperation#setJvmArguments(String...)}
     *  but those settings are not supported on the target Gradle.
     * @param failure the failure
     * @since 1.0-milestone-3
     */
    void onFailure(GradleConnectionException failure);
}
