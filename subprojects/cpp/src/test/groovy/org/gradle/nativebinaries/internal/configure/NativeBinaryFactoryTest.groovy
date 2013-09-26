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

package org.gradle.nativebinaries.internal.configure
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.nativebinaries.BuildType
import org.gradle.nativebinaries.Platform
import org.gradle.nativebinaries.internal.DefaultExecutable
import org.gradle.nativebinaries.internal.DefaultExecutableBinary
import org.gradle.nativebinaries.internal.DefaultFlavor
import org.gradle.nativebinaries.internal.ToolChainInternal
import spock.lang.Specification

class NativeBinaryFactoryTest extends Specification {
    def project = Mock(ProjectInternal)

    def toolChain = Mock(ToolChainInternal)
    def platform = Mock(Platform)
    def buildType = Mock(BuildType)

    def defaultFlavor = new DefaultFlavor(DefaultFlavor.DEFAULT)
    def flavor1 = new DefaultFlavor("flavor1")
    def component = new DefaultExecutable("name", new DirectInstantiator())


    def "does not use flavor in names when component has only one configured flavor"() {
        when:
        component.flavors.add(flavor1)

        and:
        def factory = new NativeBinaryFactory(new DirectInstantiator(), project, [], [], [])
        def binary = factory.createNativeBinary(DefaultExecutableBinary, component, toolChain, platform, buildType, flavor1)

        then:
        binary.namingScheme.lifecycleTaskName == 'nameExecutable'
        binary.namingScheme.outputDirectoryBase == 'nameExecutable'
        binary.namingScheme.getTaskName("link") == 'linkNameExecutable'
        binary.namingScheme.getTaskName("compile", "cpp") == 'compileNameExecutableCpp'
    }

    def "includes flavor in names when component has multiple flavors"() {
        when:
        component.flavors.add(defaultFlavor)
        component.flavors.add(flavor1)

        and:
        def factory = new NativeBinaryFactory(new DirectInstantiator(), project, [], [], [])
        def binary = factory.createNativeBinary(DefaultExecutableBinary, component, toolChain, platform, buildType, flavor1)

        then:
        binary.namingScheme.lifecycleTaskName == 'flavor1NameExecutable'
        binary.namingScheme.outputDirectoryBase == 'nameExecutable/flavor1'
        binary.namingScheme.getTaskName("link") == 'linkFlavor1NameExecutable'
        binary.namingScheme.getTaskName("compile", "cpp") == 'compileFlavor1NameExecutableCpp'
    }

    def "includes tool chain in names when building with multiple tool chains"() {
        when:
        component.flavors.add(defaultFlavor)
        component.flavors.add(flavor1)

        and:
        def toolChain2 = Stub(ToolChainInternal) {
            getName() >> "toolChain2"
        }

        and:
        def factory = new NativeBinaryFactory(new DirectInstantiator(), project, [toolChain, toolChain2], [], [])
        def binary = factory.createNativeBinary(DefaultExecutableBinary, component, toolChain2, platform, buildType, flavor1)

        then:
        binary.namingScheme.lifecycleTaskName == 'toolChain2Flavor1NameExecutable'
        binary.namingScheme.outputDirectoryBase == 'nameExecutable/toolChain2Flavor1'
        binary.namingScheme.getTaskName("link") == 'linkToolChain2Flavor1NameExecutable'
        binary.namingScheme.getTaskName("compile", "cpp") == 'compileToolChain2Flavor1NameExecutableCpp'
    }

    def "includes platform in names when targeting multiple platforms"() {
        when:
        component.flavors.add(defaultFlavor)
        component.flavors.add(flavor1)

        and:
        def platform2 = Stub(Platform) {
            getName() >> "platform2"
        }

        and:
        def factory = new NativeBinaryFactory(new DirectInstantiator(), project, [], [platform, platform2], [])
        def binary = factory.createNativeBinary(DefaultExecutableBinary, component, toolChain, platform2, buildType, flavor1)

        then:
        binary.namingScheme.lifecycleTaskName == 'platform2Flavor1NameExecutable'
        binary.namingScheme.outputDirectoryBase == 'nameExecutable/platform2Flavor1'
        binary.namingScheme.getTaskName("link") == 'linkPlatform2Flavor1NameExecutable'
        binary.namingScheme.getTaskName("compile", "cpp") == 'compilePlatform2Flavor1NameExecutableCpp'
    }

    def "includes buildType in names when targeting multiple build types"() {
        when:
        component.flavors.add(defaultFlavor)
        component.flavors.add(flavor1)

        and:
        def buildType2 = Stub(BuildType) {
            getName() >> "buildType2"
        }

        and:
        def factory = new NativeBinaryFactory(new DirectInstantiator(), project, [], [platform], [buildType, buildType2])
        def binary = factory.createNativeBinary(DefaultExecutableBinary, component, toolChain, platform, buildType2, flavor1)

        then:
        binary.namingScheme.lifecycleTaskName == 'buildType2Flavor1NameExecutable'
        binary.namingScheme.outputDirectoryBase == 'nameExecutable/buildType2Flavor1'
        binary.namingScheme.getTaskName("link") == 'linkBuildType2Flavor1NameExecutable'
        binary.namingScheme.getTaskName("compile", "cpp") == 'compileBuildType2Flavor1NameExecutableCpp'
    }
}
