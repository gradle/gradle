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

package org.gradle.tooling.internal.consumer;

import org.gradle.api.Transformer;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.internal.OperationDescriptorInternal;
import org.gradle.tooling.events.test.TestOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionRequest;
import org.gradle.util.CollectionUtils;

import java.util.Collection;

public class TestExecutionRequest implements InternalTestExecutionRequest {
    private final Collection<InternalTestDescriptor> testDescriptors;
    private final Collection<String> testClassNames;

    public TestExecutionRequest(Iterable<TestOperationDescriptor> operationDescriptors, Collection<String> testClassNames) {
        this.testDescriptors = adapt(operationDescriptors);
        this.testClassNames = testClassNames;
    }

    @Override
    public Collection<InternalTestDescriptor> getTestExecutionDescriptors() {
        return testDescriptors;
    }

    public Collection<String> getTestClassNames() {
        return testClassNames;
    }

    private Collection<InternalTestDescriptor> adapt(Iterable<TestOperationDescriptor> operationDescriptors) {
        return CollectionUtils.collect(operationDescriptors, new Transformer<InternalTestDescriptor, OperationDescriptor>() {
            @Override
            public InternalTestDescriptor transform(OperationDescriptor operationDescriptor) {
                return (InternalTestDescriptor) ((OperationDescriptorInternal) operationDescriptor).getInternalOperationDescriptor();
            }
        });
    }
}
