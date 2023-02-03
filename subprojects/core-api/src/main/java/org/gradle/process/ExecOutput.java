/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.process;

import org.gradle.api.Incubating;
import org.gradle.api.provider.Provider;

/**
 * Provides lazy access to the output of the external process.
 *
 * @since 7.5
 */
@Incubating
public interface ExecOutput {
    /**
     * Returns a provider of the execution result.
     *
     * <p>
     * The external process is executed only once and only when the value is requested for the first
     * time.
     * </p>
     * <p>
     * If starting the process results in exception then the ensuing exception is permanently
     * propagated to callers of {@link Provider#get}, {@link Provider#getOrElse},
     * {@link Provider#getOrNull} and {@link Provider#isPresent}.
     * </p>
     *
     * @return provider of the execution result.
     */
    Provider<ExecResult> getResult();

    /**
     * Gets a handle to the content of the process' standard output.
     *
     * @return handle of the standard output of the process.
     */
    StandardStreamContent getStandardOutput();

    /**
     * Gets a handle to the content of the process' standard error output.
     *
     * @return handle of the standard error output of the process.
     */
    StandardStreamContent getStandardError();

    /**
     * A handle to access content of the process' standard stream (the standard output of the
     * standard error output).
     *
     * @since 7.5
     */
    @Incubating
    interface StandardStreamContent {
        /**
         * Gets a provider for the standard stream's content that returns it as a String. The output
         * is decoded using the default encoding of the JVM running the build.
         *
         * <p>
         * The external process is executed only once and only when the value is requested for the
         * first time.
         * </p>
         * <p>
         * If starting the process results in exception then the ensuing exception is permanently
         * propagated to callers of {@link Provider#get}, {@link Provider#getOrElse},
         * {@link Provider#getOrNull} and {@link Provider#isPresent}.
         * </p>
         */
        Provider<String> getAsText();

        /**
         * Gets a provider for the standard stream's content that returns it as a byte array.
         *
         * <p>
         * The external process is executed only once and only when the value is requested for the
         * first time.
         * </p>
         * <p>
         * If starting the process results in exception then the ensuing exception is permanently
         * propagated to callers of {@link Provider#get}, {@link Provider#getOrElse},
         * {@link Provider#getOrNull} and {@link Provider#isPresent}.
         * </p>
         */
        Provider<byte[]> getAsBytes();
    }
}
