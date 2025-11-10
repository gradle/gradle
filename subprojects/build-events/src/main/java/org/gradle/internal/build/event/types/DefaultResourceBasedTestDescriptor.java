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

package org.gradle.internal.build.event.types;

import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalResourceBasedTestDescriptor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.Serializable;

@NullMarked
public class DefaultResourceBasedTestDescriptor implements Serializable, InternalResourceBasedTestDescriptor, InternalOperationDescriptor {
    private final OperationIdentifier id;
    private final String operationName;
    private final String operationDisplayName;
    private final OperationIdentifier parentId;
    private final String taskPath;
    private final String testDisplayName;

    private final File resource;

    public DefaultResourceBasedTestDescriptor(
        OperationIdentifier id,
        String operationName,
        String operationDisplayName,
        String displayName,
        OperationIdentifier parentId,
        String taskPath,
        File resource
    ) {
        this.id = id;
        this.operationName = operationName;
        this.operationDisplayName = operationDisplayName;
        this.testDisplayName = displayName;
        this.parentId = parentId;
        this.taskPath = taskPath;
        this.resource = resource;
    }

    @Override
    public OperationIdentifier getId() {
        return id;
    }

    @Override
    public String getName() {
        return operationName;
    }

    @Override
    public String getDisplayName() {
        return operationDisplayName;
    }

    @Override
    public String getTestDisplayName() {
        return testDisplayName;
    }

    @Override
    public OperationIdentifier getParentId() {
        return parentId;
    }

    /**
     * Only known for descriptors from test execution.
     *
     * @return the task path
     */
    @Nullable
    public String getTaskPath() {
        return taskPath;
    }

    @Override
    public File getResource() {
        return resource;
    }

}
