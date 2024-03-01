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
package org.gradle.language.rc

import org.gradle.api.internal.file.TestFiles
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.ExecutableFixture
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.app.HelloWorldApp
import org.gradle.nativeplatform.fixtures.app.WindowsResourceHelloWorldApp
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.VISUALCPP

@RequiresInstalledToolChain(VISUALCPP)
class WindowsResourcesIncrementalBuildIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    HelloWorldApp helloWorldApp = new WindowsResourceHelloWorldApp()
    ExecutableFixture mainExe
    File mainResourceFile
    def unusedHeaderFile
    def compilerOutputFileNamingScheme = new CompilerOutputFileNamingSchemeFactory(TestFiles.resolver(temporaryFolder.testDirectory)).create()

    def "setup"() {
        buildFile << helloWorldApp.pluginScript
        buildFile << helloWorldApp.extraConfiguration
        buildFile << """
model {
    components {
        main(NativeExecutableSpec)
    }
}
        """

        helloWorldApp.writeSources(file("src/main"))
        unusedHeaderFile = file("src/main/headers/unused.h") << """
    #define DUMMY_HEADER_FILE
"""

        run "mainExecutable"

        mainExe = executable("build/exe/main/main")
        mainResourceFile = file("src/main/rc/resources.rc")
    }

    @Requires(IntegTestPreconditions.NotParallelExecutor)
    def "does not re-compile sources with no change"() {
        when:
        run "mainExecutable"

        then:
        allSkipped()
    }

    def "compiles and links when resource source changes"() {
        when:
        file("src/main/rc/resources.rc").text = """
#include "hello.h"

STRINGTABLE
{
    IDS_HELLO, "Goodbye"
}
"""

        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped ":compileMainExecutableMainRc", ":linkMainExecutable", ":mainExecutable"

        and:
        mainExe.exec().out == "Goodbye"
    }

    def "compiles and but does not link when resource source changes with comment only"() {
        when:
        file("src/main/rc/resources.rc") << """
// Comment added to the end of the resource file
"""

        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped ":compileMainExecutableMainRc"
        skipped ":linkMainExecutable", ":mainExecutable"
    }

    def "compiles and links when resource compiler arg changes"() {
        when:
        buildFile << """
model {
    components {
        main {
            binaries.all {
                // Use a compiler arg that will change the generated .res file
                rcCompiler.args "-DFRENCH"
            }
        }
    }
}
"""
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped ":compileMainExecutableMainRc", ":linkMainExecutable", ":mainExecutable"
    }

    def "stale .res files are removed when a resource source file is renamed"() {
        setup:
        def outputFileNameScheme = compilerOutputFileNamingScheme
                .withOutputBaseFolder(file("build/objs/main/mainRc"))
                .withObjectFileNameSuffix(".res")
        def oldResFile = outputFileNameScheme.map(mainResourceFile)
        def newResFile = outputFileNameScheme.map(file('src/main/rc/changed_resources.rc'))
        assert oldResFile.file
        assert !newResFile.file

        when:
        mainResourceFile.renameTo(file("src/main/rc/changed_resources.rc"))
        run "mainExecutable"

        then:
        executedAndNotSkipped ":compileMainExecutableMainRc"

        and:
        !oldResFile.file
        newResFile.file
    }

    def "recompiles resource when included header is changed"() {

        given: "set the generated res file timestamp to zero"
        def outputFileNameScheme = compilerOutputFileNamingScheme
                .withOutputBaseFolder(file("build/objs/main/mainRc"))
                .withObjectFileNameSuffix(".res")
        def resourceFile = outputFileNameScheme.map(mainResourceFile)

        resourceFile.lastModified = 0
        when: "Unused header is changed"
        unusedHeaderFile << """
    #define EXTRA_DEFINE
"""
        and:
        run "mainExecutable"

        then: "No resource compilation"
        skipped ":compileMainExecutableMainRc"
        resourceFile.lastModified() == 0

        when:
        file("src/main/headers/hello.h") << """
    #define EXTRA_DEFINE
"""
        and:
        run "mainExecutable"

        then: "Resource is recompiled"
        executedAndNotSkipped ":compileMainExecutableMainRc"
        resourceFile.lastModified() > 0
    }
}

