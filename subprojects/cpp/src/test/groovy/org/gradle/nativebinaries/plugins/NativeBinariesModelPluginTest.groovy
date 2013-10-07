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

package org.gradle.nativebinaries.plugins
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Task
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.TaskDependency
import org.gradle.nativebinaries.*
import org.gradle.nativebinaries.internal.ArchitectureInternal
import org.gradle.nativebinaries.internal.DefaultFlavor
import org.gradle.nativebinaries.internal.ToolChainInternal
import org.gradle.util.TestUtil
import spock.lang.Specification

class NativeBinariesModelPluginTest extends Specification {
    final def project = TestUtil.createRootProject()

    def setup() {
        project.plugins.apply(NativeBinariesModelPlugin)
    }

    def "adds model extensions"() {
        expect:
        project.executables instanceof NamedDomainObjectContainer
        project.libraries instanceof NamedDomainObjectContainer
        project.toolChains instanceof NamedDomainObjectContainer
        project.targetPlatforms instanceof NamedDomainObjectContainer
        project.buildTypes instanceof NamedDomainObjectContainer
    }

    def "adds default tool chain, target platform and build type"() {
        when:
        project.evaluate()

        then:
        one(project.toolChains).name == 'unavailable'
        with (one(project.targetPlatforms)) {
            name == 'current'
            architecture == ArchitectureInternal.TOOL_CHAIN_DEFAULT
        }
        with (one(project.buildTypes)) {
            name == 'debug'
        }
    }

    def "default tool chain is unavailable"() {
        given:
        project.evaluate()

        when:
        one(project.toolChains).target(null)

        then:
        def t = thrown(IllegalStateException)
        t.message == "No tool chain is available: [No tool chain plugin applied]"
    }

    def "adds default flavor to every component"() {
        when:
        project.executables.create "exe"
        project.libraries.create "lib"
        project.evaluate()

        then:
        one(project.executables.exe.flavors).name == DefaultFlavor.DEFAULT
        one(project.libraries.lib.flavors).name == DefaultFlavor.DEFAULT
    }

    def "does not add defaults when domain is explicitly configured"() {
        when:
        project.toolChains.add named(ToolChainInternal, "tc")
        project.targetPlatforms.add named(Platform, "platform")
        project.buildTypes.add named(BuildType, "bt")

        and:
        def exe = project.executables.create "exe"
        exe.flavors.add named(Flavor, 'flav')

        and:
        project.evaluate()

        then:
        one(project.toolChains).name == 'tc'
        one(project.targetPlatforms).name == 'platform'
        one(project.buildTypes).name == 'bt'
        one(one(project.executables).flavors).name == 'flav'

    }

    def "creates binaries for executable"() {
        when:
        project.plugins.apply(NativeBinariesModelPlugin)
        project.toolChains.add named(ToolChainInternal, "tc")
        project.targetPlatforms.add named(Platform, "platform")
        project.buildTypes.add named(BuildType, "bt")
        def executable = project.executables.create "test"
        project.evaluate()

        then:
        ExecutableBinary executableBinary = one(project.binaries) as ExecutableBinary
        with (executableBinary) {
            name == 'testExecutable'
            component == executable
            toolChain.name == "tc"
            targetPlatform.name == "platform"
            buildType.name == "bt"
        }

        and:
        executable.binaries == [executableBinary] as Set
    }

    def "creates binaries for library"() {
        when:
        project.plugins.apply(NativeBinariesModelPlugin)
        project.toolChains.add named(ToolChainInternal, "tc")
        project.targetPlatforms.add named(Platform, "platform")
        project.buildTypes.add named(BuildType, "bt")
        def library = project.libraries.create "test"
        project.evaluate()

        then:
        SharedLibraryBinary sharedLibraryBinary = project.binaries.testSharedLibrary as SharedLibraryBinary
        with (sharedLibraryBinary) {
            name == 'testSharedLibrary'
            component == library

            toolChain.name == "tc"
            targetPlatform.name == "platform"
            buildType.name == "bt"
        }

        and:
        StaticLibraryBinary staticLibraryBinary = project.binaries.testStaticLibrary as StaticLibraryBinary
        with (staticLibraryBinary) {
            name == 'testStaticLibrary'
            component == library

            toolChain.name == "tc"
            targetPlatform.name == "platform"
            buildType.name == "bt"
        }

        and:
        library.binaries.contains(sharedLibraryBinary)
        library.binaries.contains(staticLibraryBinary)
    }

    def "creates lifecycle task for each binary"() {
        when:
        project.plugins.apply(NativeBinariesModelPlugin)
        def executable = project.executables.create "exe"
        def library = project.libraries.create "lib"
        project.evaluate()

        then:
        ExecutableBinary executableBinary = project.binaries.exeExecutable as ExecutableBinary
        with (oneTask(executableBinary.buildDependencies)) {
            name == executableBinary.name
            group == BasePlugin.BUILD_GROUP
        }
        SharedLibraryBinary sharedLibraryBinary = project.binaries.libSharedLibrary as SharedLibraryBinary
        with (oneTask(sharedLibraryBinary.buildDependencies)) {
            name == sharedLibraryBinary.name
            group == BasePlugin.BUILD_GROUP
        }
        StaticLibraryBinary staticLibraryBinary = project.binaries.libStaticLibrary as StaticLibraryBinary
        with (oneTask(staticLibraryBinary.buildDependencies)) {
            name == staticLibraryBinary.name
            group == BasePlugin.BUILD_GROUP
        }
    }

    def one(def collection) {
        assert collection.size() == 1
        return collection.iterator().next()
    }

    def named(Class type, def name) {
        Stub(type) {
            getName() >> name
        }
    }

    Task oneTask(TaskDependency dependencies) {
        def tasks = dependencies.getDependencies(Stub(Task))
        assert tasks.size() == 1
        return tasks.asList()[0]
    }
}
