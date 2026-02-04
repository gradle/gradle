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
import org.gradle.tooling.internal.protocol.events.InternalSourceAwareTestDescriptor;
import org.gradle.tooling.internal.protocol.test.source.InternalTestSource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

@NullMarked
public class DefaultTestDescriptor implements Serializable, InternalSourceAwareTestDescriptor, InternalOperationDescriptor {

    private final OperationIdentifier id;
    private final String operationName;
    private final String operationDisplayName;
    private final String testKind;
    private final String suiteName;
    private final String className;
    private final String methodName;
    private final OperationIdentifier parentId;
    private final String testDisplayName;
    private final String taskPath;
    private final InternalTestSource testSource;

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
        String taskPath,
        InternalTestSource testSource
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
        this.testSource = testSource;
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

    @Nullable
    public String getTaskPath() {
        return taskPath;
    }

    @Override
    public InternalTestSource getSource() {
        return testSource;
    }
}
