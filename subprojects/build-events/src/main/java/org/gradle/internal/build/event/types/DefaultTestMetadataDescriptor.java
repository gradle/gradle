/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.build.event.types;

import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalTestMetadataDescriptor;

import java.io.Serializable;

/**
 * Default implementation of the {@code InternalTestMetadataDescriptor} interface.
 */
public final class DefaultTestMetadataDescriptor implements Serializable, InternalTestMetadataDescriptor {
    private final OperationIdentifier id;
    private final OperationIdentifier parentId;

    public DefaultTestMetadataDescriptor(OperationIdentifier id, OperationIdentifier parentId) {
        this.id = id;
        this.parentId = parentId;
    }

    @Override
    public OperationIdentifier getId() {
        return id;
    }

    @Override
    public String getName() {
        return "metadata";
    }

    @Override
    public String getDisplayName() {
        return "metadata";
    }

    @Override
    public OperationIdentifier getParentId() {
        return parentId;
    }
}
