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

package org.gradle.api.testing.engines.testng;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.plugins.jvm.TestEngine;
import org.gradle.api.plugins.jvm.TestEngineConfigurationParameters;

import javax.inject.Inject;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

abstract public class TestNGTestEngine implements TestEngine<TestNGTestEngineRegistration> {
    public static final String DEFAULT_VERSION = "1.0.4";
    public static final String DEFAULT_API_VERSION = "7.5";

    @Override
    public Class<? extends TestEngineConfigurationParameters> getConfigurationParametersType() {
        return TestNGConfigurationParameters.class;
    }

    @Inject
    public TestNGTestEngine() {
    }

    @Override
    public Iterable<Dependency> getImplementationDependencies() {
        return emptyList();
    }

    @Override
    public Iterable<Dependency> getCompileOnlyDependencies() {
        return singletonList(getDependencyFactory().create("org.testng:testng:" + getRegistration().getApiVersion().get()));
    }

    @Override
    public Iterable<Dependency> getRuntimeOnlyDependencies() {
        return singletonList(getDependencyFactory().create("org.junit.support:testng-engine:" + getRegistration().getVersion().get()));
    }
}
