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

package org.gradle.invocation

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

class BuildStartedDeprecatedIntegrationTest extends AbstractIntegrationSpec {
    private static final String INIT_FILE_NAME = "init.gradle"

    @Unroll
    def "shows deprecation warning when adding build listener through Gradle.addBuildListener that override the BuildAdapter#buildStarted after the build was started (#fromScript)"() {
        def initScriptFile = file(INIT_FILE_NAME).touch()
        file(scriptFile) << """
            gradle.addBuildListener(new BuildAdapter() {
                void buildStarted(Gradle gradle) {
                    assert false
                }
            })
        """

        executer.usingInitScript(initScriptFile)

        expect:
        executer.expectDeprecationWarning("BuildListener#buildStarted(Gradle) has been deprecated. This is scheduled to be removed in Gradle 7.0.")
        run "help"

        where:
        fromScript       | scriptFile
        "init script"    | INIT_FILE_NAME
        "setting script" | "settings.gradle"
        "build script"   | "build.gradle"
    }

    @Unroll
    def "shows deprecation warning when adding build listener through Gradle.addListener that override the BuildAdapter#buildStarted after the build was started (#fromScript)"() {
        def initScriptFile = file(INIT_FILE_NAME).touch()
        file(scriptFile) << """
            gradle.addListener(new BuildAdapter() {
                void buildStarted(Gradle gradle) {
                    assert false
                }
            })
        """

        executer.usingInitScript(initScriptFile)

        expect:
        executer.expectDeprecationWarning("BuildListener#buildStarted(Gradle) has been deprecated. This is scheduled to be removed in Gradle 7.0.")
        run "help"

        where:
        fromScript       | scriptFile
        "init script"    | INIT_FILE_NAME
        "setting script" | "settings.gradle"
        "build script"   | "build.gradle"
    }

    @Unroll
    def "shows deprecation warning when adding build listener through Gradle.buildStarted after the build was started (#fromScript)"() {
        def initScriptFile = file(INIT_FILE_NAME).touch()
        file(scriptFile) << """
            gradle.buildStarted { assert false }
            gradle.buildStarted new Action<Gradle>() {
                void execute(Gradle g) {
                    assert false
                }
            }
        """

        executer.usingInitScript(initScriptFile)

        expect:
        executer.expectDeprecationWarning("Gradle#buildStarted(Action) has been deprecated. This is scheduled to be removed in Gradle 7.0.")
        executer.expectDeprecationWarning("Gradle#buildStarted(Closure) has been deprecated. This is scheduled to be removed in Gradle 7.0.")
        run "help"

        where:
        fromScript       | scriptFile
        "init script"    | INIT_FILE_NAME
        "setting script" | "settings.gradle"
        "build script"   | "build.gradle"
    }

    @Unroll
    def "does not shows deprecation warning when adding build listener through Gradle.addBuildListener that does not override the BuildAdapter#buildStarted after the build was started (#fromScript)"() {
        def initScriptFile = file(INIT_FILE_NAME).touch()
        file(scriptFile) << """
            gradle.addBuildListener(new BuildAdapter() {
                void buildFinished(Gradle gradle) {
                    assert true
                }
            })
        """

        executer.usingInitScript(initScriptFile)

        expect:
        run "help"

        where:
        fromScript       | scriptFile
        "init script"    | INIT_FILE_NAME
        "setting script" | "settings.gradle"
        "build script"   | "build.gradle"
    }

    @Unroll
    def "does not shows deprecation warning when adding build listener through Gradle.addListener that does not override the BuildAdapter#buildStarted after the build was started (#fromScript)"() {
        def initScriptFile = file(INIT_FILE_NAME).touch()
        file(scriptFile) << """
            gradle.addListener(new BuildAdapter() {
                void buildFinished(Gradle gradle) {
                    assert true
                }
            })
        """

        executer.usingInitScript(initScriptFile)

        expect:
        run "help"

        where:
        fromScript       | scriptFile
        "init script"    | INIT_FILE_NAME
        "setting script" | "settings.gradle"
        "build script"   | "build.gradle"
    }
}
