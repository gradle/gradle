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
import org.gradle.StartParameter;
import org.gradle.api.Transformer;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;
import org.gradle.tooling.internal.protocol.test.InternalTestMethod;
import org.gradle.tooling.internal.provider.test.ProviderInternalTestExecutionRequest;
import org.gradle.tooling.internal.provider.test.ProviderInternalTestMethod;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TestExecutionRequestAction extends SubscribableBuildAction implements ProviderInternalTestExecutionRequest {
    private final StartParameter startParameter;
    private final Set<InternalTestDescriptor> testDescriptors;
    private final Set<String> testClassNames;
    private final Set<InternalTestMethod> testMethods;
    private final Set<String> explicitTestClassNames;

    public TestExecutionRequestAction(ProviderInternalTestExecutionRequest testExecutionRequest, StartParameter startParameter, BuildClientSubscriptions clientSubscriptions) {
        super(clientSubscriptions);
        this.startParameter = startParameter;
        // Unpack the request to serialize across to the daemon
        this.testDescriptors = ImmutableSet.copyOf(testExecutionRequest.getTestExecutionDescriptors());
        this.testClassNames = ImmutableSet.copyOf(testExecutionRequest.getTestClassNames());
        this.explicitTestClassNames = ImmutableSet.copyOf(testExecutionRequest.getExplicitRequestedTestClassNames(testExecutionRequest.getTestClassNames()));
        this.testMethods = ImmutableSet.copyOf(toProviderInternalTestMethod(testExecutionRequest.getTestMethods(Collections.<InternalTestMethod>emptyList())));
    }

    private List<InternalTestMethod> toProviderInternalTestMethod(Collection<InternalTestMethod> internalTestMethods) {
        return CollectionUtils.collect(internalTestMethods, new Transformer<InternalTestMethod, InternalTestMethod>() {
            @Override
            public InternalTestMethod transform(InternalTestMethod internalTestMethod) {
                return new ProviderInternalTestMethod(internalTestMethod.getClassName(), internalTestMethod.getMethodName());
            }
        });
    }

    @Override
    public StartParameter getStartParameter() {
        return startParameter;
    }

    @Override
    public Collection<String> getTestClassNames() {
        return testClassNames;
    }

    @Override
    public Collection<InternalTestMethod> getTestMethods(Collection<InternalTestMethod> defaults) {
        return testMethods;
    }

    @Override
    public Collection<String> getExplicitRequestedTestClassNames(Collection<String> defaults) {
        return explicitTestClassNames;
    }

    @Override
    public Collection<InternalTestDescriptor> getTestExecutionDescriptors() {
        return testDescriptors;
    }

    public ProviderInternalTestExecutionRequest getTestExecutionRequest() {
        return this;
    }
}
