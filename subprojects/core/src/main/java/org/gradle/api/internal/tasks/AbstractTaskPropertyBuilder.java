/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.tasks.TaskPropertyBuilder;

abstract class AbstractTaskPropertyBuilder implements TaskPropertyBuilder {
    private String propertyName;

    public String getPropertyName() {
        return propertyName;
    }

    protected void setPropertyName(String propertyName) {
        if (propertyName != null) {
            if (propertyName.length() == 0) {
                throw new IllegalArgumentException("Property name must not be empty string");
            }
            if (!Character.isJavaIdentifierStart(propertyName.codePointAt(0))) {
                throw new IllegalArgumentException(String.format("Property name '%s' must be a valid Java identifier", propertyName));
            }
            boolean previousCharWasADot = false;
            for (int idx = 1; idx < propertyName.length(); idx++) {
                int chr = propertyName.codePointAt(idx);
                // Allow single dots except as the last element of the name
                if (chr == '.' && !previousCharWasADot && idx < propertyName.length() - 1) {
                    previousCharWasADot = true;
                    continue;
                }
                if (!Character.isJavaIdentifierPart(chr)) {
                    throw new IllegalArgumentException(String.format("Property name '%s' must be a valid Java identifier", propertyName));
                }
                previousCharWasADot = false;
            }
        }
        this.propertyName = propertyName;
    }

    @Override
    public String toString() {
        return propertyName == null ? "<unnamed>" : propertyName;
    }
}
