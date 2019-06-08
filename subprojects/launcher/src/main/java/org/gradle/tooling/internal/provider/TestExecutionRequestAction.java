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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Transformer;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;
import org.gradle.tooling.internal.protocol.test.InternalJvmTestRequest;
import org.gradle.tooling.internal.provider.test.ProviderInternalJvmTestRequest;
import org.gradle.tooling.internal.provider.test.ProviderInternalTestExecutionRequest;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TestExecutionRequestAction extends SubscribableBuildAction {
    private final StartParameterInternal startParameter;
    private final Set<InternalTestDescriptor> testDescriptors;
    private final Set<String> classNames;
    private final Set<InternalJvmTestRequest> internalJvmTestRequests;

    private TestExecutionRequestAction(BuildClientSubscriptions clientSubscriptions, StartParameterInternal startParameter, Set<InternalTestDescriptor> testDescriptors, Set<String> providerClassNames, Set<InternalJvmTestRequest> internalJvmTestRequests) {
        super(clientSubscriptions);
        this.startParameter = startParameter;
        this.testDescriptors = testDescriptors;
        this.classNames = providerClassNames;
        this.internalJvmTestRequests = internalJvmTestRequests;
    }

    // Unpacks the request to serialize across to the daemon and creates instance of
    // TestExecutionRequestAction
    public static TestExecutionRequestAction create(BuildClientSubscriptions clientSubscriptions, StartParameterInternal startParameter, ProviderInternalTestExecutionRequest testExecutionRequest) {
        final Collection<String> testClassNames = testExecutionRequest.getTestClassNames();
        final Collection<InternalJvmTestRequest> internalJvmTestRequests = testExecutionRequest.getInternalJvmTestRequests(Collections.<InternalJvmTestRequest>emptyList());
        Set<InternalJvmTestRequest> providerInternalJvmTestRequests = ImmutableSet.copyOf(toProviderInternalJvmTestRequest(internalJvmTestRequests, testClassNames));
        return new TestExecutionRequestAction(clientSubscriptions, startParameter,
                                                ImmutableSet.copyOf(testExecutionRequest.getTestExecutionDescriptors()),
                                                ImmutableSet.copyOf(testClassNames),
                                                providerInternalJvmTestRequests);
    }

    private static List<InternalJvmTestRequest> toProviderInternalJvmTestRequest(Collection<InternalJvmTestRequest> internalJvmTestRequests, Collection<String> testClassNames) {
        // handle consumer < 2.7
        if(internalJvmTestRequests.isEmpty()){
            return CollectionUtils.collect(testClassNames, new Transformer<InternalJvmTestRequest, String>() {
                @Override
                public InternalJvmTestRequest transform(String testClass) {
                    return new ProviderInternalJvmTestRequest(testClass, null);
                }
            });
        } else {
            return CollectionUtils.collect(internalJvmTestRequests, new Transformer<InternalJvmTestRequest, InternalJvmTestRequest>() {
                @Override
                public InternalJvmTestRequest transform(InternalJvmTestRequest internalTestMethod) {
                    return new ProviderInternalJvmTestRequest(internalTestMethod.getClassName(), internalTestMethod.getMethodName());
                }
            });
        }
    }

    @Override
    public StartParameterInternal getStartParameter() {
        return startParameter;
    }

    public Collection<String> getTestClassNames() {
        return classNames;
    }

    public Collection<InternalJvmTestRequest> getInternalJvmTestRequests() {
        return internalJvmTestRequests;
    }

    public Collection<InternalTestDescriptor> getTestExecutionDescriptors() {
        return testDescriptors;
    }
}
