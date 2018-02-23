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
package org.gradle.api.internal.tasks.testing.results;

import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;

import javax.annotation.Nullable;

public class UnknownTestDescriptor implements TestDescriptorInternal {

    @Override
    public Object getId() {
        return "Unknown test (possible bug, please report)";
    }

    @Override
    public String getName() {
        return "Unknown test (possible bug, please report)";
    }

    @Override
    public String getClassName() {
        return null;
    }

    @Override
    public boolean isComposite() {
        return false;
    }

    @Override
    public TestDescriptorInternal getParent() {
        return null;
    }

    @Nullable
    @Override
    public Object getOwnerBuildOperationId() {
        return null;
    }
}
