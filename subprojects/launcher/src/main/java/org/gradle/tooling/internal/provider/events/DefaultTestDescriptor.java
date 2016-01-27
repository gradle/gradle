/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.tooling.internal.provider.events;

import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor;

import java.io.Serializable;

public class DefaultTestDescriptor implements Serializable, InternalJvmTestDescriptor, InternalOperationDescriptor {

    private final Object id;
    private final String name;
    private final String displayName;
    private final String testKind;
    private final String suiteName;
    private final String className;
    private final String methodName;
    private final Object parentId;
    private String taskPath;

    public DefaultTestDescriptor(Object id, String name, String displayName, String testKind, String suiteName, String className, String methodName, Object parentId, String taskPath) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.testKind = testKind;
        this.suiteName = suiteName;
        this.className = className;
        this.methodName = methodName;
        this.parentId = parentId;
        this.taskPath = taskPath;
    }

    @Override
    public Object getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getTestKind() {
        return testKind;
    }

    @Override
    public String getSuiteName() {
        return suiteName;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getMethodName() {
        return methodName;
    }

    @Override
    public Object getParentId() {
        return parentId;
    }

    public String getTaskPath() {
        return taskPath;
    }
}
