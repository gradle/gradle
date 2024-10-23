/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Incubating;
import org.gradle.api.NonNullApi;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.internal.scan.UsedByScanPlugin;

import javax.annotation.Nullable;

@UsedByScanPlugin("test-distribution")
@NonNullApi
public abstract class AbstractTestDescriptor implements TestDescriptorInternal {
    private final Object id;
    private final String name;

    public AbstractTestDescriptor(Object id, String name) {
        this.id = id;
        this.name = name;
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
    public String getClassName() {
        return null;
    }

    /**
     * Returns the method name for this test, if any.
     * It should be in TestDescriptor, but moved here for backward compatibility
     *  TODO: move it to TestDescriptor interface with 9.0
     *
     * @return The method name. May return null.
     * @since 8.8
     * @see org.gradle.tooling.internal.provider.runner.TestOperationMapper#getLegacyOperationDisplayName(String, TestDescriptor)
     */
    @Incubating
    @Nullable
    public String getMethodName() {
        return null;
    }

    @Override
    public TestDescriptorInternal getParent() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return getName();
    }

    @Override
    public String getClassDisplayName() {
        return getClassName();
    }

}
