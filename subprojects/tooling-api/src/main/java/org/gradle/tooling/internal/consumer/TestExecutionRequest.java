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

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.Transformer;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.internal.OperationDescriptorWrapper;
import org.gradle.tooling.events.test.TestOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionRequestVersion2;
import org.gradle.tooling.internal.protocol.test.InternalTestMethod;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class TestExecutionRequest implements InternalTestExecutionRequestVersion2 {
    private final Collection<InternalTestDescriptor> testDescriptors;
    private final Collection<String> testClassNames;
    private final Collection<InternalTestMethod> testMethods;

    public TestExecutionRequest(Iterable<TestOperationDescriptor> operationDescriptors, Collection<String> testClassNames, ImmutableMultimap<String, String> testMethods) {
        this.testMethods = adaptTestMethods(testMethods);
        this.testDescriptors = adaptDescriptors(operationDescriptors);
        this.testClassNames = testClassNames;
    }

    @Override
    public Collection<InternalTestDescriptor> getTestExecutionDescriptors() {
        return testDescriptors;
    }

    public Collection<String> getTestClassNames() {
        return testClassNames;
    }

    @Override
    public Collection<InternalTestMethod> getTestMethods() {
        return testMethods;
    }

    private Collection<InternalTestDescriptor> adaptDescriptors(Iterable<TestOperationDescriptor> operationDescriptors) {
        return CollectionUtils.collect(operationDescriptors, new Transformer<InternalTestDescriptor, OperationDescriptor>() {
            @Override
            public InternalTestDescriptor transform(OperationDescriptor operationDescriptor) {
                return (InternalTestDescriptor) ((OperationDescriptorWrapper) operationDescriptor).getInternalOperationDescriptor();
            }
        });
    }

    private Set<InternalTestMethod> adaptTestMethods(Multimap<String, String> testMethods) {
        Set<InternalTestMethod> testMethodsSet = new LinkedHashSet<InternalTestMethod>(testMethods.size());
        final Collection<Map.Entry<String, String>> entries = testMethods.entries();
        for (Map.Entry<String, String> entry : entries) {
            testMethodsSet.add(new DefaultTestMethod(entry.getKey(), entry.getValue()));
        }
        return testMethodsSet;
    }
}
