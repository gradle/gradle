/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.failure.mappers;

import junit.framework.ComparisonFailure;

public class CustomComparisonFailure extends ComparisonFailure {

    /**
     * Constructs a comparison failure.
     *
     * @param message  the identifying message or null
     * @param expected the expected string value
     * @param actual   the actual string value
     */
    public CustomComparisonFailure(String message, String expected, String actual) {
        super(message, expected, actual);
    }
}
