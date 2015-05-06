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

public class InternalBuildOperation implements IdentifiableOperation {
    final String id;
    final Object payload;
    final InternalBuildOperation parent;

    public InternalBuildOperation(String id, Object payload, InternalBuildOperation parent) {
        this.id = id;
        this.payload = payload;
        this.parent = parent;
    }

    public Object getId() {
        return parent==null?id:parent.getId()+":"+id;
    }

    public Object getPayload() {
        return payload;
    }

    @Nullable
    public InternalBuildOperation getParent() {
        return parent;
    }

    @Nullable
    public Object getParentId() {
        return parent==null?null:parent.getId();
    }
}
