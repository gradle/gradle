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
import org.gradle.tooling.TestExecutionException;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.internal.OperationDescriptorInternal;
import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTaskDescriptor;
import org.gradle.tooling.internal.protocol.test.InternalJvmTestExecutionDescriptor;
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionRequest;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class TestExecutionRequest implements InternalTestExecutionRequest {
    private final Collection<InternalJvmTestExecutionDescriptor> jvmTestExecutionDescriptors;
    private Set<String> testClassNames;

    public TestExecutionRequest(List<OperationDescriptor> operationDescriptors, Set<String> testClassNames) {
        this.jvmTestExecutionDescriptors = adapt(operationDescriptors);
        this.testClassNames = testClassNames;
    }

    @Override
    public Collection<InternalJvmTestExecutionDescriptor> getTestExecutionDescriptors() {
        return jvmTestExecutionDescriptors;
    }

    public Collection<String> getTestClassNames() {
        return testClassNames;
    }

    private Collection<InternalJvmTestExecutionDescriptor> adapt(List<OperationDescriptor> operationDescriptors) {
        return CollectionUtils.collect(operationDescriptors, new Transformer<InternalJvmTestExecutionDescriptor, OperationDescriptor>() {
            @Override
            public InternalJvmTestExecutionDescriptor transform(OperationDescriptor operationDescriptor) {
                final InternalOperationDescriptor internalOperationDescriptor = ((OperationDescriptorInternal) operationDescriptor).getInternalOperationDescriptor();
                if (internalOperationDescriptor instanceof InternalJvmTestDescriptor) {
                    InternalJvmTestDescriptor jvmTestOperationDescriptor = (InternalJvmTestDescriptor)internalOperationDescriptor;
                    return new DefaultInternalJvmTestExecutionDescriptor(jvmTestOperationDescriptor.getClassName(), jvmTestOperationDescriptor.getMethodName(), jvmTestOperationDescriptor.getTaskPath());
                } else if (internalOperationDescriptor instanceof InternalTaskDescriptor) {
                    final InternalTaskDescriptor taskOperationDescriptor = (InternalTaskDescriptor) internalOperationDescriptor;
                    return new DefaultInternalJvmTestExecutionDescriptor(null, null, taskOperationDescriptor.getTaskPath());
                } else {
                    throw new TestExecutionException("Invalid TestOperationDescriptor implementation. Only JvmTestOperationDescriptor supported.");
                }
            }
        });
    }
}
