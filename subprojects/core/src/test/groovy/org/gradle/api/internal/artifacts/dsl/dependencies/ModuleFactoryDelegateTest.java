/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl.dependencies;

import groovy.lang.Closure;
import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultClientModule;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;

import static org.junit.Assert.assertThat;

public class ModuleFactoryDelegateTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    private DependencyFactory dependencyFactoryStub = context.mock(DependencyFactory.class);
    private ClientModule clientModule = new DefaultClientModule("junit", "junit", "4.4");
    
    private ModuleFactoryDelegate moduleFactoryDelegate = new ModuleFactoryDelegate(clientModule, dependencyFactoryStub);

    @Test
    public void dependency() {
        final String dependencyNotation = "someNotation";
        final ModuleDependency dependencyDummy = context.mock(ModuleDependency.class);
        letFactoryStubReturnDependency(dependencyNotation, dependencyDummy);
        moduleFactoryDelegate.dependency(dependencyNotation);
        assertThat(clientModule.getDependencies(), Matchers.equalTo(WrapUtil.toSet(dependencyDummy)));
    }

    @Test
    public void dependencyWithClosure() {
        final String dependencyNotation = "someNotation";
        final Closure configureClosure = HelperUtil.toClosure("{}");
        final ModuleDependency dependencyDummy = context.mock(ModuleDependency.class);
        letFactoryStubReturnDependency(dependencyNotation, dependencyDummy);
        moduleFactoryDelegate.dependency(dependencyNotation, configureClosure);
        assertThat(clientModule.getDependencies(), Matchers.equalTo(WrapUtil.toSet(dependencyDummy)));
    }

    @Test
    public void dependencies() {
        final String dependencyNotation1 = "someNotation1";
        final String dependencyNotation2 = "someNotation2";
        final ModuleDependency dependencyDummy1 = context.mock(ModuleDependency.class, "dep1");
        final ModuleDependency dependencyDummy2 = context.mock(ModuleDependency.class, "dep2");
        letFactoryStubReturnDependency(dependencyNotation1, dependencyDummy1);
        letFactoryStubReturnDependency(dependencyNotation2, dependencyDummy2);
        moduleFactoryDelegate.dependencies((Object[])WrapUtil.toArray(dependencyNotation1, dependencyNotation2));
        assertThat(clientModule.getDependencies(), Matchers.equalTo(WrapUtil.toSet(dependencyDummy1, dependencyDummy2)));
    }

    private void letFactoryStubReturnDependency(final String dependencyNotation, final Dependency dependencyDummy) {
        context.checking(new Expectations() {{
            allowing(dependencyFactoryStub).createDependency(dependencyNotation);
            will(returnValue(dependencyDummy));
        }});
    }

    @Test
    public void module() {
        final String clientModuleNotation = "someNotation";
        final Closure configureClosure = HelperUtil.toClosure("{}");
        final ClientModule clientModuleDummy = context.mock(ClientModule.class);
        context.checking(new Expectations() {{
            allowing(dependencyFactoryStub).createModule(clientModuleNotation, configureClosure);
            will(returnValue(clientModuleDummy));
        }});
        moduleFactoryDelegate.module(clientModuleNotation, configureClosure);
        assertThat(this.clientModule.getDependencies(), Matchers.equalTo(WrapUtil.<ModuleDependency>toSet(clientModuleDummy)));
    }
}
