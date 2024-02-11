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
package org.gradle.api.internal.artifacts.dsl.dependencies

import org.gradle.api.artifacts.ClientModule
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultClientModule
import org.gradle.util.TestUtil
import org.gradle.util.internal.WrapUtil
import spock.lang.Specification

class ModuleFactoryDelegateTest extends Specification {

    private DependencyFactoryInternal dependencyFactoryStub = Mock()
    private ClientModule clientModule = new DefaultClientModule("junit", "junit", "4.4")

    private ModuleFactoryDelegate moduleFactoryDelegate = new ModuleFactoryDelegate(clientModule, dependencyFactoryStub)

    def dependency() {
        final String dependencyNotation = "someNotation"
        final ModuleDependency dependencyDummy = Mock(ModuleDependency.class)

        when:
        1 * dependencyFactoryStub.createDependency(dependencyNotation) >> dependencyDummy

        and:
        moduleFactoryDelegate.dependency(dependencyNotation)

        then:
        clientModule.getDependencies() == [dependencyDummy] as Set
    }

    def dependencyWithClosure() {
        final String dependencyNotation = "someNotation"
        final ModuleDependency dependencyDummy = Mock(ModuleDependency)

        when:
        1 * dependencyFactoryStub.createDependency(dependencyNotation) >> dependencyDummy

        and:
        moduleFactoryDelegate.dependency(dependencyNotation, TestUtil.toClosure("{}"))

        then:
        clientModule.getDependencies() == [dependencyDummy] as Set
    }

    def dependencies() {
        final String dependencyNotation1 = "someNotation1"
        final String dependencyNotation2 = "someNotation2"
        final ModuleDependency dependencyDummy1 = Mock(ModuleDependency)
        final ModuleDependency dependencyDummy2 = Mock(ModuleDependency)

        when:
        1 * dependencyFactoryStub.createDependency(dependencyNotation1) >> dependencyDummy1
        1 * dependencyFactoryStub.createDependency(dependencyNotation2) >> dependencyDummy2

        and:
        moduleFactoryDelegate.dependencies((Object[])WrapUtil.toArray(dependencyNotation1, dependencyNotation2))

        then:
        clientModule.getDependencies() == [dependencyDummy1, dependencyDummy2] as Set
    }

    def module() {
        final String clientModuleNotation = "someNotation"
        final Closure configureClosure = TestUtil.toClosure("{}")
        final ClientModule clientModuleDummy = Mock(ClientModule)

        when:
        1 * dependencyFactoryStub.createModule(clientModuleNotation, configureClosure) >> clientModuleDummy

        and:
        moduleFactoryDelegate.module(clientModuleNotation, configureClosure)

        then:
        clientModule.dependencies == [clientModuleDummy] as Set
    }
}
