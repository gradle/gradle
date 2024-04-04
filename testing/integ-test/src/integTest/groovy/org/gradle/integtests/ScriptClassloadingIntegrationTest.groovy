/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.LeaksFileHandles
import spock.lang.Issue

import static org.gradle.integtests.ScriptClassloadingIntegrationTest.SharedScriptFileType.SHARED_BUILDFILE
import static org.gradle.integtests.ScriptClassloadingIntegrationTest.SharedScriptFileType.SHARED_SCRIPTPLUGIN

/**
 * Tests for classloading related bugs build scripts.
 */
class ScriptClassloadingIntegrationTest extends AbstractIntegrationSpec {
    enum SharedScriptFileType {
        SHARED_BUILDFILE,
        SHARED_SCRIPTPLUGIN
    }

    @Issue(['GRADLE-3526', 'GRADLE-3553'])
    @LeaksFileHandles
    def 'apply the same script file causing different buildscript classpaths in different projects #sharedScriptFileType'(SharedScriptFileType sharedScriptFileType) {
        given:
        def subprojectNames = ['project1', 'project2']
        multiProjectBuild('root', subprojectNames) {
            file('script.gradle') << """
                buildscript {
                    File searchDir = project.projectDir
                    def version = new File(searchDir, 'version.txt').text
                    ${mavenCentralRepository()}
                    dependencies {
                        // Dynamically changing the classpath here surfaces problems with the ClassLoaderCache
                        classpath "org.apache.commons:commons-lang3:\${version}"
                    }
                }

                task doStringOp {
                    doLast {
                        println org.apache.commons.lang3.StringUtils.join('Hello', 'world')
                    }
                }
            """.stripIndent()

            file('project1/version.txt') << '3.4'
            file('project2/version.txt') << '3.3'

            switch (sharedScriptFileType) {
                case SHARED_SCRIPTPLUGIN:
                    // share the same script file by applying it as a script plugin in the subproject's build file
                    subprojectNames.each { subprojectName ->
                        file("${subprojectName}/build.gradle") << """
                            apply from: new File(rootDir, 'script.gradle')
                        """.stripIndent()
                    }
                    break
                case SHARED_BUILDFILE:
                    // share the same script file by setting project.buildFileName in settings.gradle
                    subprojectNames.each { subprojectName ->
                        settingsFile << """
                            project(':${subprojectName}').buildFileName = '../script.gradle'
                        """.stripIndent()
                    }
                    break
            }
        }

        executer.requireOwnGradleUserHomeDir()

        expect:
        succeeds('doStringOp')
        // The problem only surfaces on the second run when we start with a clean Gradle user home
        succeeds('doStringOp')

        where:
        sharedScriptFileType << [SHARED_SCRIPTPLUGIN, SHARED_BUILDFILE]
    }
}
