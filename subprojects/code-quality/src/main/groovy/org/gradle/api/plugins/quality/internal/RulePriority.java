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

package org.gradle.api.plugins.quality.internal;

import org.gradle.api.InvalidUserDataException;

public abstract class RulePriority {
    /**
     * Validates the value is a valid PMD RulePriority (1-5)
     * @param value rule priority threshold
     */
    public static void validate(int value) {
        if (value > 5 || value < 1) {
            throw new InvalidUserDataException(String.format("Invalid rulePriority '%d'.  Valid range 1 (highest) to 5 (lowest).", value));
        }
    }
}
