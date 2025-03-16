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

package org.gradle.language

abstract class AbstractNativeProductionComponentDependenciesIntegrationTest extends AbstractNativeDependenciesIntegrationTest {
    def "can define different implementation dependencies on each binary"() {
        given:
        createDirs("lib")
        settingsFile << 'include "lib"'
        makeComponentWithLibrary()
        buildFile << """
            ${componentUnderTestDsl} {
                binaries.getByName('mainDebug').configure {
                    dependencies {
                        implementation project(':lib')
                    }
                }
            }
        """

        when:
        run(':assembleDebug')

        then:
        result.assertTasksExecuted(libDebugTasks, assembleDebugTasks, ':assembleDebug')

        when:
        run(':assembleRelease')

        then:
        result.assertTasksExecuted(assembleReleaseTasks, ':assembleRelease')
    }

    def "can define an included build implementation dependency on a binary"() {
        settingsFile << 'includeBuild "lib"'
        makeComponentWithIncludedBuildLibrary()
        buildFile << """
            ${componentUnderTestDsl} {
                binaries.getByName('mainDebug').configure {
                    dependencies {
                        implementation 'org.gradle.test:lib:1.0'
                    }
                }
            }
        """

        when:
        run(':assembleDebug')

        then:
        result.assertTasksExecuted(libDebugTasks, assembleDebugTasks, ':assembleDebug')

        when:
        run(':assembleRelease')

        then:
        result.assertTasksExecuted(assembleReleaseTasks, ':assembleRelease')
    }

    @Override
    protected String getAssembleDevBinaryTask() {
        return ":assembleDebug"
    }

    @Override
    protected List<String> getAssembleDevBinaryTasks() {
        return getAssembleDebugTasks()
    }

    protected abstract List<String> getAssembleDebugTasks()

    protected abstract List<String> getAssembleReleaseTasks()

    protected abstract void makeComponentWithIncludedBuildLibrary()
}
