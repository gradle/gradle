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

import org.gradle.api.Nullable;
import org.gradle.api.tasks.testing.TestDescriptor;

public interface TestDescriptorInternal extends TestDescriptor {
    @Nullable
    @Override
    TestDescriptorInternal getParent();

    Object getId();

    /**
     * Returns the identifier for the build operation (eg test task) that owns this test.
     * Not null only for a root test suite with no parent test.
     */
    @Nullable
    Object getOwnerBuildOperationId();
}
