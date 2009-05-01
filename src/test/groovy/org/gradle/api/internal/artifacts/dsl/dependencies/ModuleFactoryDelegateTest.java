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
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dependencies.DefaultClientModule;
import org.gradle.api.internal.artifacts.dsl.dependencies.ModuleFactoryDelegate;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertThat;
import org.junit.Test;

/**
 * @author Hans Dockter
 */
public class ModuleFactoryDelegateTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    private DependencyFactory dependencyFactoryStub = context.mock(DependencyFactory.class);
    private ClientModule clientModule = new DefaultClientModule("junit", "junit", "4.4");
    
    private ModuleFactoryDelegate moduleFactoryDelegate = new ModuleFactoryDelegate(clientModule, dependencyFactoryStub);

    @Test
    public void dependency() {
        final String dependencyNotation = "someNotation";
        final Dependency dependencyDummy = context.mock(Dependency.class);
        letFactoryStubReturnDependency(dependencyNotation, dependencyDummy, null, true);
        moduleFactoryDelegate.dependency(dependencyNotation);
        assertThat(clientModule.getDependencies(), Matchers.equalTo(WrapUtil.toSet(dependencyDummy)));
    }

    @Test
    public void dependencyWithClosure() {
        final String dependencyNotation = "someNotation";
        final Closure configureClosure = HelperUtil.toClosure("{}");
        final Dependency dependencyDummy = context.mock(Dependency.class);
        letFactoryStubReturnDependency(dependencyNotation, dependencyDummy, configureClosure, true);
        moduleFactoryDelegate.dependency(dependencyNotation, configureClosure);
        assertThat(clientModule.getDependencies(), Matchers.equalTo(WrapUtil.toSet(dependencyDummy)));
    }

    @Test
    public void dependencies() {
        final String dependencyNotation1 = "someNotation1";
        final String dependencyNotation2 = "someNotation2";
        final Dependency dependencyDummy1 = context.mock(Dependency.class, "dep1");
        final Dependency dependencyDummy2 = context.mock(Dependency.class, "dep2");
        letFactoryStubReturnDependency(dependencyNotation1, dependencyDummy1, null, false);
        letFactoryStubReturnDependency(dependencyNotation2, dependencyDummy2, null, false);
        moduleFactoryDelegate.dependencies(WrapUtil.toArray(dependencyNotation1, dependencyNotation2));
        assertThat(clientModule.getDependencies(), Matchers.equalTo(WrapUtil.toSet(dependencyDummy1, dependencyDummy2)));
    }

    private void letFactoryStubReturnDependency(final String dependencyNotation, final Dependency dependencyDummy,
                                                final Closure configureClosure, final boolean declareClosureIfNull) {
        context.checking(new Expectations() {{
            if (configureClosure == null && !declareClosureIfNull) {
                allowing(dependencyFactoryStub).createDependency(dependencyNotation);
            } else {
                allowing(dependencyFactoryStub).createDependency(dependencyNotation, configureClosure);
            }
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
        assertThat(this.clientModule.getDependencies(), Matchers.equalTo(WrapUtil.<Dependency>toSet(clientModuleDummy)));
    }
}
