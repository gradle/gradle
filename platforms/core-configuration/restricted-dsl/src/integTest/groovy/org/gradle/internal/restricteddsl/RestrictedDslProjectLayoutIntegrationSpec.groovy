/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.restricteddsl

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.initialization.AbstractProjectSpecificationContainer.*

class RestrictedDslProjectLayoutIntegrationSpec extends AbstractIntegrationSpec {

    public static final ArrayList<String> SETTINGS_SCRIPTS = ["settings.gradle.something", "settings.gradle.kts"]

    def "can create nested projects with restricted DSL (#settingsScript)"() {
        given:
        file(settingsScript) << """
            build {
                name = "test-value"
            }

            layout {
                subproject("foo") {
                    subproject("bar") { }
                }
                subproject("baz") {
                    subproject("qux") {
                        subproject("qax") { }
                    }
                    subproject("fizz") { }
                }
            }
        """
        buildFile << """
            println('name = ' + rootProject.name)
            assert project(':foo').projectDir == file('foo')
            assert project(':foo:bar').projectDir == file('foo/bar')
            assert project(':baz').projectDir == file('baz')
            assert project(':baz:qux').projectDir == file('baz/qux')
            assert project(':baz:qux:qax').projectDir == file('baz/qux/qax')
        """
        file("foo/build.gradle") << ""
        file("foo/bar/build.gradle") << ""
        file("baz/build.gradle") << ""
        file("baz/qux/build.gradle") << ""
        file("baz/qux/qax/build.gradle") << ""
        file("baz/fizz/build.gradle") << ""

        expect:
        succeeds("projects")
        outputContains("name = test-value")
        outputContains("--- Project ':foo:bar'")
        outputContains("--- Project ':baz:qux:qax'")
        outputContains("--- Project ':baz:fizz'")

        where:
        settingsScript << SETTINGS_SCRIPTS
    }

    def "can set physical dir different than logical dir (#settingsScript)"() {
        given:
        file(settingsScript) << """
            build {
                name = "test-value"
            }

            layout {
                subproject("foo") {
                    setProjectDirRelativePath("applications/foo")
                    subproject("bar") {
                        setProjectDirRelativePath("libraries/bar")
                    }
                }
                subproject("baz") {
                    setProjectDirRelativePath("applications/baz")
                    subproject("qux") {
                        setProjectDirRelativePath("libraries/qux")
                        subproject("qax") {
                            setProjectDirRelativePath("../qax")
                        }
                    }
                    subproject("fizz") { }
                }
            }
        """
        buildFile << """
            println('name = ' + rootProject.name)
            assert project(':foo').projectDir == file('applications/foo')
            assert project(':foo:bar').projectDir == file('applications/foo/libraries/bar')
            assert project(':baz').projectDir == file('applications/baz')
            assert project(':baz:qux').projectDir == file('applications/baz/libraries/qux')
            assert project(':baz:qux:qax').projectDir == file('applications/baz/libraries/qax')
        """
        file("applications/foo/build.gradle") << ""
        file("applications/foo/libraries/bar/build.gradle") << ""
        file("applications/baz/build.gradle") << ""
        file("applications/baz/libraries/qux/build.gradle") << ""
        file("applications/baz/libraries/qax/build.gradle") << ""
        file("applications/baz/fizz/build.gradle") << ""

        expect:
        succeeds("projects")
        outputContains("name = test-value")
        outputContains("--- Project ':foo:bar'")
        outputContains("--- Project ':baz:qux:qax'")
        outputContains("--- Project ':baz:fizz'")

        where:
        settingsScript << SETTINGS_SCRIPTS
    }

    def "cannot add the same logical path twice (#settingsScript)"() {
        given:
        file(settingsScript) << """
            build {
                name = "test-value"
            }

            layout {
                subproject("foo") { }
                subproject("foo") { }
            }
        """
        file("foo/build.gradle") << ""

        expect:
        args("--stacktrace")
        fails("projects")

        and:
        // Exceptions are not yet thrown with context aware causes, so we just check for the cause in the
        // stack trace.
        //failureHasCause("A project with path ':foo' has already been registered.")
        errorOutput.contains("A project with path ':foo' has already been registered.")

        where:
        settingsScript << SETTINGS_SCRIPTS
    }

    def "cannot add the same physical path twice (#settingsScript)"() {
        given:
        file(settingsScript) << """
            build {
                name = "test-value"
            }

            layout {
                subproject("foo") { }
                subproject("bar") {
                    setProjectDirRelativePath("foo")
                }
            }
        """
        file("foo/build.gradle") << ""

        expect:
        args("--stacktrace")
        fails("projects")

        and:
        // Exceptions are not yet thrown with context aware causes, so we just check for the cause in the
        // stack trace.
        //failureHasCause("A project with directory 'foo' has already been registered.")
        errorOutput.contains("A project with directory 'foo' has already been registered.")

        where:
        settingsScript << SETTINGS_SCRIPTS
    }

    def "can autodetect projects in a multi-project workspace (#settingsScript)"() {
        given:
        file(settingsScript) << """
            build {
                name = "test-value"
            }

            layout {

            }
        """
        buildFile << """
            println('name = ' + rootProject.name)
            assert project(':foo').projectDir == file('foo')
            assert project(':foo:bar').projectDir == file('foo/bar')
            assert project(':baz').projectDir == file('baz')
            assert project(':baz:qux').projectDir == file('baz/qux')
            assert project(':baz:qux:qax').projectDir == file('baz/qux/qax')
        """
        file("foo/${PROJECT_MARKER_FILE}") << ""
        file("foo/bar/${PROJECT_MARKER_FILE}") << ""
        file("baz/${PROJECT_MARKER_FILE}") << ""
        file("baz/qux/${PROJECT_MARKER_FILE}") << ""
        file("baz/qux/qax/${PROJECT_MARKER_FILE}") << ""
        file("baz/fizz/${PROJECT_MARKER_FILE}") << ""

        expect:
        succeeds("projects")
        outputContains("--- Project ':foo:bar'")
        outputContains("--- Project ':baz:qux:qax'")
        outputContains("--- Project ':baz:fizz'")

        where:
        settingsScript << SETTINGS_SCRIPTS
    }
}
