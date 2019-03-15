/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.execution.history.changes;

import org.gradle.api.InvalidUserDataException;

public interface IncrementalInputProperties {
    boolean isIncrementalProperty(String propertyName);
    String getPropertyNameFor(Object value);

    IncrementalInputProperties NONE = new IncrementalInputProperties() {
        @Override
        public boolean isIncrementalProperty(String propertyName) {
            return false;
        }

        @Override
        public String getPropertyNameFor(Object value) {
            throw new InvalidUserDataException("Cannot query incremental changes for property " + value + ": No incremental properties declared.");
        }
    };

    IncrementalInputProperties ALL = new IncrementalInputProperties() {
        @Override
        public boolean isIncrementalProperty(String propertyName) {
            return true;
        }

        @Override
        public String getPropertyNameFor(Object value) {
            throw new InvalidUserDataException("Cannot query incremental changes for property " + value + ": Requires using 'InputChanges'.");
        }
    };
}
