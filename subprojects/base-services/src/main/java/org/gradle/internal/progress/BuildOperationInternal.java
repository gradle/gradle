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
package org.gradle.internal.progress;

import org.gradle.api.Nullable;

/**
 *
 * This class is consumed by the build scan plugin.
 *
 * TODO remove this subclass, consider moving complete content of this package to 'operations'
 * */
public final class BuildOperationInternal extends BuildOperationDescriptor {

    protected BuildOperationInternal(Object id, Object parentId, String name, String displayName, String progressDisplayName, Object details) {
        super(id, parentId, name, displayName, progressDisplayName, details);
    }

    /**
     * Arbitrary metadata for the operation.
     */
    @Nullable
    public Object getOperationDescriptor() {
        return getDetails();
    }
}
