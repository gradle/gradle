/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.swift.plugins

import org.gradle.api.file.RegularFile
import org.gradle.internal.os.OperatingSystem
import org.gradle.language.swift.SwiftBinary
import org.gradle.language.swift.SwiftBundle
import org.gradle.language.swift.SwiftExecutable
import org.gradle.language.swift.SwiftSharedLibrary
import org.gradle.language.swift.tasks.CreateSwiftBundle
import org.gradle.language.swift.tasks.SwiftCompile
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkMachOBundle
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import spock.lang.Specification

class SwiftBasePluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("test").build()

    def "adds compile task for component"() {
        def binary = Stub(SwiftBinary)
        binary.name >> name
        binary.module >> project.objects.property(String)

        when:
        project.pluginManager.apply(SwiftBasePlugin)
        project.components.add(binary)

        then:
        def compileSwift = project.tasks[taskName]
        compileSwift instanceof SwiftCompile
        compileSwift.objectFileDir.get().asFile == projectDir.file("build/obj/${objDir}")

        where:
        name        | taskName                | objDir
        "main"      | "compileSwift"          | "main"
        "mainDebug" | "compileDebugSwift"     | "main/debug"
        "test"      | "compileTestSwift"      | "test"
        "testDebug" | "compileTestDebugSwift" | "test/debug"
    }

    def "adds link and install task for executable"() {
        def module = project.objects.property(String)
        module.set("TestApp")
        def executable = Stub(SwiftExecutable)
        executable.name >> name
        executable.module >> module

        when:
        project.pluginManager.apply(SwiftBasePlugin)
        project.components.add(executable)

        then:
        def link = project.tasks[linkTask]
        link instanceof LinkExecutable
        link.binaryFile.get().asFile == projectDir.file("build/exe/$exeDir" + OperatingSystem.current().getExecutableName("TestApp"))

        def install = project.tasks[installTask]
        install instanceof InstallExecutable
        install.installDirectory.get().asFile == projectDir.file("build/install/$exeDir")

        where:
        name        | linkTask        | installTask        | exeDir
        "main"      | "link"          | "install"          | "main/"
        "mainDebug" | "linkDebug"     | "installDebug"     | "main/debug/"
        "test"      | "linkTest"      | "installTest"      | "test/"
        "testDebug" | "linkTestDebug" | "installTestDebug" | "test/debug/"
    }

    def "adds link task for shared library"() {
        def module = project.objects.property(String)
        module.set("TestLib")
        def library = Stub(SwiftSharedLibrary)
        library.name >> name
        library.module >> module

        when:
        project.pluginManager.apply(SwiftBasePlugin)
        project.components.add(library)

        then:
        def link = project.tasks[taskName]
        link instanceof LinkSharedLibrary
        link.binaryFile.get().asFile == projectDir.file("build/lib/${libDir}" + OperatingSystem.current().getSharedLibraryName("TestLib"))

        where:
        name        | taskName        | libDir
        "main"      | "link"          | "main/"
        "mainDebug" | "linkDebug"     | "main/debug/"
        "test"      | "linkTest"      | "test/"
        "testDebug" | "linkTestDebug" | "test/debug/"
    }

    def "adds link task for bundle"() {
        def module = project.objects.property(String)
        module.set("TestBundle")
        def infoPlist = project.objects.property(RegularFile)
        def bundleBinary = Stub(SwiftBundle)
        bundleBinary.name >> name
        bundleBinary.module >> module
        bundleBinary.informationPropertyList >> infoPlist

        when:
        project.pluginManager.apply(SwiftBasePlugin)
        project.components.add(bundleBinary)

        then:
        def link = project.tasks[linkTaskName]
        link instanceof LinkMachOBundle
        link.binaryFile.get().asFile == projectDir.file("build/exe/${bundleDir}" + OperatingSystem.current().getExecutableName("TestBundle"))

        and:
        def bundleTask = project.tasks[bundleTaskName]
        bundleTask instanceof CreateSwiftBundle
        bundleTask.outputDir.get().asFile == projectDir.file("build/bundle/${bundleDir}TestBundle.xctest")

        where:
        name        | linkTaskName    | bundleTaskName         | bundleDir
        "main"      | "link"          | "bundleSwift"          | "main/"
        "mainDebug" | "linkDebug"     | "bundleSwiftDebug"     | "main/debug/"
        "test"      | "linkTest"      | "bundleSwiftTest"      | "test/"
        "testDebug" | "linkTestDebug" | "bundleSwiftTestDebug" | "test/debug/"
    }
}
