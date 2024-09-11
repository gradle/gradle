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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.NonNullApi;
import org.gradle.internal.id.CompositeIdGenerator;

import javax.annotation.Nullable;

@NonNullApi
public class DefaultParameterizedTestDescriptor extends DefaultTestSuiteDescriptor {

    private final CompositeIdGenerator.CompositeId parentId;
    private final String className;
    private final String displayName;

    public DefaultParameterizedTestDescriptor(Object id, String methodName, @Nullable String className, String displayName, CompositeIdGenerator.CompositeId parentId) {
        super(id, methodName);
        this.className = className;
        this.displayName = displayName;
        this.parentId = parentId;
    }

    public CompositeIdGenerator.CompositeId getParentId() {
        return parentId;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getMethodName() {
        return getName();
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return "Test method " + getMethodName();
    }
}
