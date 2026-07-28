/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.testretry.internal.executer;

import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;

import javax.annotation.Nullable;

final class TestDescriptorImpl implements TestDescriptorInternal {

    private final Object syntheticTestId;
    private final TestDescriptorInternal parent;
    private final String testName;

    public TestDescriptorImpl(Object testId, TestDescriptorInternal parent, String testName) {
        this.syntheticTestId = testId;
        this.parent = parent;
        this.testName = testName;
    }

    @Override
    public TestDescriptorInternal getParent() {
        return null;
    }

    @Override
    public Object getId() {
        return syntheticTestId;
    }

    @Nullable
    @Override
    public String getClassName() {
        return parent.getClassName();
    }

    @Override
    public String getClassDisplayName() {
        return parent.getClassDisplayName();
    }

    @Override
    public String getName() {
        return testName;
    }

    @Override
    public String getDisplayName() {
        return testName;
    }

    @Override
    public boolean isComposite() {
        return false;
    }
}
