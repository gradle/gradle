/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api;

/**
 * <p>A <code>BuildCancelledException</code> is thrown when a build is interrupted due to cancellation request.
 *
 * @since 2.1
 */
public class BuildCancelledException extends GradleException {
    public BuildCancelledException() {
        this("Build cancelled.");
    }

    public BuildCancelledException(String message) {
        super(message);
    }

    public BuildCancelledException(String message, Throwable cause) {
        super(message, cause);
    }
}
