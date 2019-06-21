/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.work.ChangeType;

public enum ChangeTypeInternal {
    ADDED("has been added", ChangeType.ADDED),
    MODIFIED("has changed", ChangeType.MODIFIED),
    REMOVED("has been removed", ChangeType.REMOVED);

    private final String description;
    private final ChangeType publicType;

    ChangeTypeInternal(String description, ChangeType publicType) {
        this.description = description;
        this.publicType = publicType;
    }

    public String describe() {
        return description;
    }

    public ChangeType getPublicType() {
        return publicType;
    }
}
