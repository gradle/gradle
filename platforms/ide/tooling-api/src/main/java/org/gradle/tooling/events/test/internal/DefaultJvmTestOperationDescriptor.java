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

package org.gradle.tooling.events.test.internal;

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.test.JvmTestKind;
import org.gradle.tooling.events.test.JvmTestOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor;

import javax.annotation.Nullable;

/**
 * Implementation of the {@code JvmTestOperationDescriptor} interface.
 */
public final class DefaultJvmTestOperationDescriptor extends DefaultTestOperationDescriptor implements JvmTestOperationDescriptor {
    private final JvmTestKind jvmTestKind;
    private final String suiteName;
    private final String className;
    private final String methodName;

    public DefaultJvmTestOperationDescriptor(InternalJvmTestDescriptor internalJvmTestDescriptor, OperationDescriptor parent, JvmTestKind jvmTestKind, String suiteName, String className, String methodName) {
        super(internalJvmTestDescriptor, parent);
        this.jvmTestKind = jvmTestKind;
        this.suiteName = suiteName;
        this.className = className;
        this.methodName = methodName;
    }

    @Override
    public JvmTestKind getJvmTestKind() {
        return this.jvmTestKind;
    }

    @Nullable
    @Override
    public String getSuiteName() {
        return this.suiteName;
    }

    @Nullable
    @Override
    public String getClassName() {
        return this.className;
    }

    @Nullable
    @Override
    public String getMethodName() {
        return this.methodName;
    }
}
