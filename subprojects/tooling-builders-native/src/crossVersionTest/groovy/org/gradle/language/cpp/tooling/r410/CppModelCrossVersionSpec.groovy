/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.language.cpp.tooling.r410

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.cpp.CppApplication
import org.gradle.tooling.model.cpp.CppExecutable
import org.gradle.tooling.model.cpp.CppLibrary
import org.gradle.tooling.model.cpp.CppProject
import org.gradle.tooling.model.cpp.CppSharedLibrary
import org.gradle.tooling.model.cpp.CppStaticLibrary
import org.gradle.tooling.model.cpp.CppTestSuite

@ToolingApiVersion(">=4.10")
@TargetGradleVersion(">=4.10")
class CppModelCrossVersionSpec extends ToolingApiSpecification {
    def "has empty model when root project does not apply any C++ plugins"() {
        buildFile << """
            apply plugin: 'java-library'
        """

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.projectIdentifier.projectPath == ':'
        project.projectIdentifier.buildIdentifier.rootDir == projectDir
        project.mainComponent == null
        project.testComponent == null
    }

    def "can query model when root project applies C++ application plugin"() {
        settingsFile << """
            rootProject.name = 'app'
        """
        buildFile << """
            apply plugin: 'cpp-application'
        """
        def headerDir = file('src/main/headers')
        def src1 = file('src/main/cpp/app.cpp').createFile()
        def src2 = file('src/main/cpp/app-impl.cpp').createFile()

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.projectIdentifier.projectPath == ':'
        project.projectIdentifier.buildIdentifier.rootDir == projectDir

        project.mainComponent instanceof CppApplication
        project.mainComponent.baseName == 'app'

        project.mainComponent.binaries.size() == 2

        project.mainComponent.binaries[0] instanceof CppExecutable
        project.mainComponent.binaries[0].name == 'mainDebug'
        project.mainComponent.binaries[0].baseName == 'app'
        project.mainComponent.binaries[0].compilationDetails.sources as Set == [src1, src2] as Set
        project.mainComponent.binaries[0].compilationDetails.frameworkSearchPaths.empty
        !project.mainComponent.binaries[0].compilationDetails.systemHeaderSearchPaths.empty
        project.mainComponent.binaries[0].compilationDetails.userHeaderSearchPaths == [headerDir]

        project.mainComponent.binaries[1] instanceof CppExecutable
        project.mainComponent.binaries[1].name == 'mainRelease'
        project.mainComponent.binaries[1].baseName == 'app'
        project.mainComponent.binaries[1].compilationDetails.sources as Set == [src1, src2] as Set
        project.mainComponent.binaries[1].compilationDetails.frameworkSearchPaths.empty
        !project.mainComponent.binaries[1].compilationDetails.systemHeaderSearchPaths.empty
        project.mainComponent.binaries[1].compilationDetails.userHeaderSearchPaths == [headerDir]

        project.testComponent == null
    }

    def "can query model when root project applies C++ library plugin"() {
        settingsFile << """
            rootProject.name = 'lib'
        """
        buildFile << """
            apply plugin: 'cpp-library'
        """
        def headerDir = file('src/main/headers')
        def apiHeaderDir = file('src/main/public')
        def src1 = file('src/main/cpp/lib.cpp').createFile()
        def src2 = file('src/main/cpp/lib-impl.cpp').createFile()

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.mainComponent instanceof CppLibrary
        project.mainComponent.baseName == 'lib'

        project.mainComponent.binaries.size() == 2
        project.mainComponent.binaries[0] instanceof CppSharedLibrary
        project.mainComponent.binaries[0].name == 'mainDebug'
        project.mainComponent.binaries[0].baseName == 'lib'
        project.mainComponent.binaries[0].compilationDetails.sources as Set == [src1, src2] as Set
        project.mainComponent.binaries[0].compilationDetails.frameworkSearchPaths.empty
        !project.mainComponent.binaries[0].compilationDetails.systemHeaderSearchPaths.empty
        project.mainComponent.binaries[0].compilationDetails.userHeaderSearchPaths == [apiHeaderDir, headerDir]

        project.mainComponent.binaries[1] instanceof CppSharedLibrary
        project.mainComponent.binaries[1].name == 'mainRelease'
        project.mainComponent.binaries[1].baseName == 'lib'
        project.mainComponent.binaries[1].compilationDetails.sources as Set == [src1, src2] as Set
        project.mainComponent.binaries[1].compilationDetails.frameworkSearchPaths.empty
        !project.mainComponent.binaries[1].compilationDetails.systemHeaderSearchPaths.empty
        project.mainComponent.binaries[1].compilationDetails.userHeaderSearchPaths == [apiHeaderDir, headerDir]

        project.testComponent == null
    }

    def "can query model when root project applies C++ unit test plugin"() {
        settingsFile << """
            rootProject.name = 'tests'
        """
        buildFile << """
            apply plugin: 'cpp-unit-test'
        """
        def headerDir = file('src/test/headers')
        def src1 = file('src/test/cpp/test-main.cpp').createFile()
        def src2 = file('src/test/cpp/test2.cpp').createFile()

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.mainComponent == null
        project.testComponent instanceof CppTestSuite
        project.testComponent.baseName == 'testsTest'

        project.testComponent.binaries.size() == 1
        project.testComponent.binaries[0] instanceof CppExecutable
        project.testComponent.binaries[0].name == 'testExecutable'
        project.testComponent.binaries[0].baseName == 'testsTest'
        project.testComponent.binaries[0].compilationDetails.sources as Set == [src1, src2] as Set
        project.testComponent.binaries[0].compilationDetails.frameworkSearchPaths.empty
        !project.testComponent.binaries[0].compilationDetails.systemHeaderSearchPaths.empty
        project.testComponent.binaries[0].compilationDetails.userHeaderSearchPaths == [headerDir]
    }

    def "can query model when root project applies C++ application and unit test plugins"() {
        settingsFile << """
            rootProject.name = 'app'
        """
        buildFile << """
            apply plugin: 'cpp-application'
            apply plugin: 'cpp-unit-test'
        """

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.mainComponent instanceof CppApplication
        project.mainComponent.baseName == 'app'
        project.testComponent instanceof CppTestSuite
        project.testComponent.baseName == 'appTest'
        project.testComponent.binaries[0].compilationDetails.userHeaderSearchPaths == [file('src/test/headers'), file('src/main/headers')]
    }

    def "can query model when root project applies C++ library and unit test plugins"() {
        buildFile << """
            apply plugin: 'cpp-library'
            apply plugin: 'cpp-unit-test'
        """

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.mainComponent instanceof CppLibrary
        project.testComponent instanceof CppTestSuite
        project.testComponent.binaries[0].compilationDetails.userHeaderSearchPaths == [file('src/test/headers'), file('src/main/public'), file('src/main/headers')]
    }

    def "can query model for customized C++ application"() {
        settingsFile << """
            rootProject.name = 'app'
        """
        buildFile << """
            apply plugin: 'cpp-application'
            application {
                baseName = 'some-app'
                source.from 'src'
                privateHeaders.from = ['include']
            }
        """
        def headerDir = file('include')
        def src1 = file('src/main/cpp/app.cpp').createFile()
        def src2 = file('src/app-impl.cpp').createFile()

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.mainComponent instanceof CppApplication
        project.mainComponent.baseName == 'some-app'
        project.mainComponent.binaries.size() == 2

        project.mainComponent.binaries[0] instanceof CppExecutable
        project.mainComponent.binaries[0].name == 'mainDebug'
        project.mainComponent.binaries[0].baseName == 'some-app'
        project.mainComponent.binaries[0].compilationDetails.sources as Set == [src1, src2] as Set
        project.mainComponent.binaries[0].compilationDetails.userHeaderSearchPaths == [headerDir]

        project.mainComponent.binaries[1] instanceof CppExecutable
        project.mainComponent.binaries[1].name == 'mainRelease'
        project.mainComponent.binaries[1].baseName == 'some-app'
        project.mainComponent.binaries[1].compilationDetails.sources as Set == [src1, src2] as Set
        project.mainComponent.binaries[1].compilationDetails.userHeaderSearchPaths == [headerDir]
    }

    def "can query model for customized C++ library"() {
        settingsFile << """
            rootProject.name = 'lib'
        """
        buildFile << """
            apply plugin: 'cpp-library'
            library {
                baseName = 'some-lib'
                linkage = [Linkage.STATIC, Linkage.SHARED]
                privateHeaders.from = []
                publicHeaders.from = ['include']
            }
        """
        def publicHeaders = file('include')

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.mainComponent instanceof CppLibrary
        project.mainComponent.baseName == 'some-lib'

        project.mainComponent.binaries.size() == 4

        project.mainComponent.binaries[0] instanceof CppStaticLibrary
        project.mainComponent.binaries[0].name == 'mainDebugStatic'
        project.mainComponent.binaries[0].baseName == 'some-lib'
        project.mainComponent.binaries[0].compilationDetails.userHeaderSearchPaths == [publicHeaders]
        project.mainComponent.binaries[1] instanceof CppSharedLibrary
        project.mainComponent.binaries[1].name == 'mainDebugShared'
        project.mainComponent.binaries[1].baseName == 'some-lib'
        project.mainComponent.binaries[1].compilationDetails.userHeaderSearchPaths == [publicHeaders]
        project.mainComponent.binaries[2] instanceof CppStaticLibrary
        project.mainComponent.binaries[2].name == 'mainReleaseStatic'
        project.mainComponent.binaries[2].baseName == 'some-lib'
        project.mainComponent.binaries[2].compilationDetails.userHeaderSearchPaths == [publicHeaders]
        project.mainComponent.binaries[3] instanceof CppSharedLibrary
        project.mainComponent.binaries[3].name == 'mainReleaseShared'
        project.mainComponent.binaries[3].baseName == 'some-lib'
        project.mainComponent.binaries[3].compilationDetails.userHeaderSearchPaths == [publicHeaders]
    }

    def "can query the models for each project in a build"() {
        settingsFile << """
            include 'app'
            include 'lib'
            include 'other'
        """
        buildFile << """
            project(':app') { 
                apply plugin: 'cpp-application'
                application {
                    dependencies { implementation project(':lib') }
                }
            }
            project(':lib') { 
                apply plugin: 'cpp-library' 
                apply plugin: 'cpp-unit-test' 
            }
        """

        when:
        def models = withConnection { connection -> connection.action(new FetchAllCppProjects()).run() }

        then:
        models.size() == 4
        models[0].projectIdentifier.projectPath == ':'
        models[0].mainComponent == null
        models[0].testComponent == null
        models[1].projectIdentifier.projectPath == ':app'
        models[1].mainComponent instanceof CppApplication
        models[1].mainComponent.binaries[0].compilationDetails.userHeaderSearchPaths == [file("app/src/main/headers"), file("lib/src/main/public")]
        models[1].testComponent == null
        models[2].projectIdentifier.projectPath == ':lib'
        models[2].mainComponent instanceof CppLibrary
        models[2].mainComponent.binaries[0].compilationDetails.userHeaderSearchPaths == [file("lib/src/main/public"), file("lib/src/main/headers")]
        models[2].testComponent != null
        models[3].projectIdentifier.projectPath == ':other'
        models[3].mainComponent == null
        models[3].testComponent == null
    }

    def "can query the models for each project in a composite build"() {
        settingsFile << """
            include 'app'
            includeBuild 'lib'
        """
        buildFile << """
            project(':app') { 
                apply plugin: 'cpp-application' 
            }
        """
        file("lib/build.gradle") << """
                apply plugin: 'cpp-library' 
                apply plugin: 'cpp-unit-test' 
        """

        when:
        def models = withConnection { connection -> connection.action(new FetchAllCppProjects()).run() }

        then:
        models.size() == 3
        models[0].projectIdentifier.projectPath == ':'
        models[0].projectIdentifier.buildIdentifier.rootDir == projectDir
        models[0].mainComponent == null
        models[0].testComponent == null
        models[1].projectIdentifier.projectPath == ':app'
        models[1].projectIdentifier.buildIdentifier.rootDir == projectDir
        models[1].mainComponent instanceof CppApplication
        models[1].testComponent == null
        models[2].projectIdentifier.projectPath == ':'
        models[2].projectIdentifier.buildIdentifier.rootDir == file('lib')
        models[2].mainComponent instanceof CppLibrary
        models[2].testComponent != null
    }
}
