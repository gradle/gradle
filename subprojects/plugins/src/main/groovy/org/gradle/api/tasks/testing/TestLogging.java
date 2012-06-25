/*
 * Copyright 2012 the original author or authors.
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

/**
 * Configures logging of the test execution, e.g. whether the std err / out should be eagerly shown.
 *
 * @deprecated use {@link org.gradle.api.tasks.testing.logging.TestLogging} instead
 */
@Deprecated
public interface TestLogging {
    /**
     * Whether to show eagerly the standard stream events. Standard output is printed at INFO level, standard error at ERROR level.
     *
     * @param standardStreams to configure
     * @return this logging instance
     */
    TestLogging setShowStandardStreams(boolean standardStreams);

    /**
     * Whether to show eagerly the standard stream events. Standard output is printed at INFO level, standard error at ERROR level.
     */
    boolean getShowStandardStreams();
}