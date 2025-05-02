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

public interface InputFileChanges extends ChangeContainer {
    boolean accept(String propertyName, ChangeVisitor visitor);

    InputFileChanges EMPTY = new InputFileChanges() {

        @Override
        public boolean accept(ChangeVisitor visitor) {
            return true;
        }

        @Override
        public boolean accept(String propertyName, ChangeVisitor visitor) {
            throw new InvalidUserDataException("Cannot query incremental changes for property " + propertyName + ": No incremental properties declared.");
        }
    };
}
