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

import org.gradle.api.NonNullApi;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;

import javax.annotation.Nullable;
import java.io.Serializable;

@NonNullApi
public class DefaultTestDescriptor implements Serializable, InternalJvmTestDescriptor, InternalOperationDescriptor {

    private final OperationIdentifier id;
    private final String operationName;
    private final String operationDisplayName;
    private final String testKind;
    private final String suiteName;
    private final String className;
    private final String methodName;
    private final OperationIdentifier parentId;
    private final String taskPath;
    private final String testDisplayName;

    public DefaultTestDescriptor(
        OperationIdentifier id,
        String operationName,
        String operationDisplayName,
        String displayName,
        String testKind,
        @Nullable String suiteName,
        @Nullable String className,
        @Nullable String methodName,
        OperationIdentifier parentId,
        String taskPath
    ) {
        this.id = id;
        this.operationName = operationName;
        this.operationDisplayName = operationDisplayName;
        this.testDisplayName = displayName;
        this.testKind = testKind;
        this.suiteName = suiteName;
        this.className = className;
        this.methodName = methodName;
        this.parentId = parentId;
        this.taskPath = taskPath;
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
    public String getTestKind() {
        return testKind;
    }

    @Override
    @Nullable
    public String getSuiteName() {
        return suiteName;
    }

    @Override
    @Nullable
    public String getClassName() {
        return className;
    }

    @Override
    @Nullable
    public String getMethodName() {
        return methodName;
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
}
