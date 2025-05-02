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

package org.gradle.api.tasks.testing.logging;

/**
 * Determines how exceptions are formatted in test logging.
 */
public enum TestExceptionFormat {
    /**
     * Short display of exceptions.  Shows the exception types and locations as well as the hierarchy of causes, but not exception messages or full stack traces.
     */
    SHORT,
    /**
     * Full display of exceptions.
     */
    FULL
}
