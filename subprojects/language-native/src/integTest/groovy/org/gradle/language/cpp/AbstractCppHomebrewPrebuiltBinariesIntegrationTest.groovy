/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.language.cpp

import org.gradle.nativeplatform.Linkage
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppLogger
import org.gradle.nativeplatform.fixtures.app.SourceElement

abstract class AbstractCppHomebrewPrebuiltBinariesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    private final HomebrewCellarFixture homebrewCellar = new HomebrewCellarFixture(file('homebrew'))

    protected abstract void makeSingleProject()

    protected String configureDependency(String coordinate) {
        return """
            ${componentUnderTestDsl} {
                dependencies {
                    implementation("${coordinate}")
                }
            }
        """
    }

    protected void makeHomebrewPackage(Linkage... linkages) {
        homebrewCellar.newPackage('logger', '1.2').with { fixture ->
            def element = new CppLogger().asLib()

            def prebuiltDir = file("homebrewPrebuilt-${fixture.packageName}")
            element.writeToProject(prebuiltDir)
            prebuiltDir.file("build.gradle") << """
                plugins {
                    id 'cpp-library'
                }
                library {
                    linkage = [Linkage.SHARED, Linkage.STATIC]
                }
            """
            prebuiltDir.file("settings.gradle") << "rootProject.name = '${fixture.getPackageName()}'"

            def tasks = []
            if (linkages.contains(Linkage.SHARED)) {
                tasks << "assembleReleaseShared"
            }
            if (linkages.contains(Linkage.STATIC)) {
                tasks << "assembleReleaseStatic"
            }
            if (!tasks.empty) {
                executer.inDirectory(prebuiltDir).withTasks(*tasks).start().waitForFinish()
            }

            fixture.includeFile("logger.h").copyFrom(prebuiltDir.file('src/main/public/logger.h'))
            if (linkages.contains(Linkage.SHARED)) {
                fixture.sharedLibraryFile(sharedLibraryName(fixture.getPackageName())).with { libraryFile ->
                    libraryFile.parentFile.mkdirs()
                    libraryFile.copyFrom(prebuiltDir.file(sharedLibraryName('build/lib/main/release/shared/logger')))
                }
            }
            if (linkages.contains(Linkage.STATIC)) {
                fixture.staticLibraryFile(staticLibraryName(fixture.getPackageName())).with { libraryFile ->
                    libraryFile.parentFile.mkdirs()
                    libraryFile.copyFrom(prebuiltDir.file(staticLibraryName('build/lib/main/release/static/logger')))
                }
            }
        }
    }

    protected abstract String getComponentUnderTestDsl()

    protected abstract SourceElement getComponentUnderTest()

    protected abstract List<String> getTasksToAssembleDevelopmentBinary()

    protected abstract String getDevelopmentBinaryCompileTask()

    protected abstract String getCompileConfigurationName()

    def "can compile library against header only library"() {
        given:
        makeSingleProject()
        makeHomebrewPackage()
        buildFile << configureDependency("logger::1.2")
        componentUnderTest.writeToProject(testDirectory)

        when:
        succeeds(developmentBinaryCompileTask)

        then:
        result.assertTasksExecutedAndNotSkipped(developmentBinaryCompileTask)
    }

    def "fails if no package"() {
        given:
        makeSingleProject()
        buildFile << configureDependency("unknown-package:logger:1.2")
        componentUnderTest.writeToProject(testDirectory)

        when:
        def failure = fails("assemble")

        then:
        failure.assertHasDescription("Execution failed for task '${developmentBinaryCompileTask}'.")
        failure.assertHasCause("Could not resolve all files for configuration ':${compileConfigurationName}'.")
        failure.assertHasCause("""Could not find unknown-package:logger:1.2.
Searched in the following locations:
  - ${homebrewCellar.location.toURL()}/unknown-package/1.2
Required by:
    project :""")
    }

    def "fails if library not found"() {
        given:
        makeSingleProject()
        buildFile << configureDependency("logger:unknown-library:1.2")
        componentUnderTest.writeToProject(testDirectory)

        when:
        def failure = fails("assemble")

        then:
        // In theory, it should fail at the link task (not finding the library)
        failure.assertHasDescription("Execution failed for task '${developmentBinaryCompileTask}'.")
        failure.assertHasCause("Could not resolve all files for configuration ':${compileConfigurationName}'.")
        failure.assertHasCause("""Could not find logger:unknown-library:1.2.
Searched in the following locations:
  - ${homebrewCellar.location.toURL()}/logger/1.2
Required by:
    project :""")
    }

    def "build against library providing both linkage"() {
        given:
        makeSingleProject()
        makeHomebrewPackage(Linkage.SHARED, Linkage.STATIC)
        buildFile << configureDependency("logger:logger:1.2")
        componentUnderTest.writeToProject(testDirectory)

        when:
        succeeds("assemble")

        then:
        result.assertTasksExecutedAndNotSkipped(tasksToAssembleDevelopmentBinary, ":assemble")
    }

    def "build against library providing only a shared linkage"() {
        given:
        makeSingleProject()
        makeHomebrewPackage(Linkage.SHARED)
        buildFile << configureDependency("logger:logger:1.2")
        componentUnderTest.writeToProject(testDirectory)

        when:
        succeeds("assemble")

        then:
        result.assertTasksExecutedAndNotSkipped(tasksToAssembleDevelopmentBinary, ":assemble")
    }

    def "build against library providing only a static linkage"() {
        given:
        makeSingleProject()
        makeHomebrewPackage(Linkage.STATIC)
        buildFile << configureDependency("logger:logger:1.2")
        componentUnderTest.writeToProject(testDirectory)

        when:
        succeeds("assemble")

        then:
        result.assertTasksExecutedAndNotSkipped(tasksToAssembleDevelopmentBinary, ":assemble")
    }
}
