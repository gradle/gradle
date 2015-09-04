/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.tasks.testing;

import org.gradle.api.GradleException;
import org.gradle.api.Incubating;

/**
 * <p>A <code>TestExecutionException</code> is thrown when no tests can be found that match the specified test filters.
 *
 * @since 2.6
 */
@Incubating
public class TestExecutionException extends GradleException {
    public TestExecutionException(String message) {
        super(message);
    }

    public TestExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
