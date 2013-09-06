/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativebinaries.internal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.nativebinaries.Flavor
import spock.lang.Specification

class NativeBinaryFactoryTest extends Specification {
    def project = Mock(ProjectInternal)

    def toolChain = Mock(ToolChainInternal)

    def flavor1 = new DefaultFlavor("flavor1")
    def component = new DefaultExecutable("name", new DirectInstantiator())

    def "does not use flavor in names name when component has only default flavor"() {
        when:
        def factory = new NativeBinaryFactory(new DirectInstantiator(), project, [])
        def binary = factory.createNativeBinary(DefaultExecutableBinary, component, toolChain, Flavor.DEFAULT)

        then:
        component.flavors == [Flavor.DEFAULT] as Set

        and:
        binary.namingScheme.lifecycleTaskName == 'nameExecutable'
        binary.namingScheme.outputDirectoryBase == 'nameExecutable'
        binary.namingScheme.getTaskName("link") == 'linkNameExecutable'
        binary.namingScheme.getTaskName("compile", "cpp") == 'compileNameExecutableCpp'
    }

    def "does not use flavor in names when component has only one configured flavor"() {
        when:
        component.flavors.add(flavor1)

        and:
        def factory = new NativeBinaryFactory(new DirectInstantiator(), project, [])
        def binary = factory.createNativeBinary(DefaultExecutableBinary, component, toolChain, flavor1)

        then:
        binary.namingScheme.lifecycleTaskName == 'nameExecutable'
        binary.namingScheme.outputDirectoryBase == 'nameExecutable'
        binary.namingScheme.getTaskName("link") == 'linkNameExecutable'
        binary.namingScheme.getTaskName("compile", "cpp") == 'compileNameExecutableCpp'
    }

    def "includes flavor in names when component has multiple flavors"() {
        when:
        component.flavors.add(Flavor.DEFAULT)
        component.flavors.add(flavor1)

        and:
        def factory = new NativeBinaryFactory(new DirectInstantiator(), project, [])
        def binary = factory.createNativeBinary(DefaultExecutableBinary, component, toolChain, flavor1)

        then:
        binary.namingScheme.lifecycleTaskName == 'flavor1NameExecutable'
        binary.namingScheme.outputDirectoryBase == 'nameExecutable/flavor1'
        binary.namingScheme.getTaskName("link") == 'linkFlavor1NameExecutable'
        binary.namingScheme.getTaskName("compile", "cpp") == 'compileFlavor1NameExecutableCpp'
    }

    def "includes tool chain in names when building with multiple tool chains"() {
        when:
        component.flavors.add(Flavor.DEFAULT)
        component.flavors.add(flavor1)

        and:
        def toolChain2 = Stub(ToolChainInternal) {
            getName() >> "toolChain2"
        }

        and:
        def factory = new NativeBinaryFactory(new DirectInstantiator(), project, [toolChain, toolChain2])
        def binary = factory.createNativeBinary(DefaultExecutableBinary, component, toolChain2, flavor1)

        then:
        binary.namingScheme.lifecycleTaskName == 'toolChain2Flavor1NameExecutable'
        binary.namingScheme.outputDirectoryBase == 'nameExecutable/toolChain2Flavor1'
        binary.namingScheme.getTaskName("link") == 'linkToolChain2Flavor1NameExecutable'
        binary.namingScheme.getTaskName("compile", "cpp") == 'compileToolChain2Flavor1NameExecutableCpp'
    }
}
