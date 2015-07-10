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

package org.gradle.tooling.internal.provider;

import org.gradle.tooling.events.OperationDescriptor;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class TestExecutionRequest {
    private List<OperationDescriptor> operationDescriptors;
    private Set<String> testClassNames;

    public TestExecutionRequest(List<OperationDescriptor> operationDescriptors, Set<String> testClassNames) {
        this.operationDescriptors = operationDescriptors;
        this.testClassNames = testClassNames;
    }

    public Collection<String> getTestClassNames() {
        return testClassNames;
    }

    public Collection<OperationDescriptor> getOperationDescriptors() {
        return operationDescriptors;
    }
}
