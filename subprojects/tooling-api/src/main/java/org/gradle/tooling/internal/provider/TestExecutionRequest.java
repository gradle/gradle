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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import org.gradle.tooling.events.test.JvmTestOperationDescriptor;
import org.gradle.tooling.events.test.TestOperationDescriptor;

import java.util.Collection;
import java.util.List;

public class TestExecutionRequest {
    private List<TestOperationDescriptor> testOperationDescriptors;

    public TestExecutionRequest(List<TestOperationDescriptor> testOperationDescriptors) {
        this.testOperationDescriptors = testOperationDescriptors;
    }

    public Collection<JvmTestOperationDescriptor> getTestOperationDescriptors() {
        final Collection<JvmTestOperationDescriptor> jvmTestOperationDescriptors = Collections2.transform(Collections2.filter(testOperationDescriptors, new Predicate<TestOperationDescriptor>() {
            @Override
            public boolean apply(TestOperationDescriptor input) {
                return input instanceof JvmTestOperationDescriptor;
            }
        }), new Function<TestOperationDescriptor, JvmTestOperationDescriptor>() {
            @Override
            public JvmTestOperationDescriptor apply(TestOperationDescriptor input) {
                return (JvmTestOperationDescriptor) input;
            }
        });

        return jvmTestOperationDescriptors;
    }
}
