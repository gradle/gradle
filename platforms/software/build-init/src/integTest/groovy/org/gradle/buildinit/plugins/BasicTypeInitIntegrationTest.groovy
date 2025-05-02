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

package org.gradle.buildinit.plugins

import org.gradle.buildinit.plugins.fixtures.ScriptDslFixture

import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl.KOTLIN
import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators


class BasicTypeInitIntegrationTest extends AbstractInitIntegrationSpec {

    @Override
    String subprojectName() { null }

    def "defaults to kotlin build scripts"() {
        when:
        run 'init'

        then:
        dslFixtureFor(KOTLIN).assertGradleFilesGenerated()
    }

    def "incubating does not break basic #scriptDsl build scripts"() {
        when:
        run('init', '--project-name', 'someApp', '--dsl', scriptDsl.id, '--incubating')

        then:
        def dslFixture = dslFixtureFor(scriptDsl)
        dslFixture.assertGradleFilesGenerated()
        dslFixture.settingsFile.assertContents(dslFixture.containsStringAssignment('rootProject.name', 'someApp'))

        when:
        run('help')

        then:
        noExceptionThrown()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "can configure root project name with #scriptDsl build scripts"() {
        when:
        run('init', '--project-name', 'someApp', '--dsl', scriptDsl.id)

        then:
        def dslFixture = dslFixtureFor(scriptDsl)
        dslFixture.assertGradleFilesGenerated()

        when:
        run('help')

        then:
        noExceptionThrown()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "merges .gitignore if it already exists with #scriptDsl build scripts"() {
        when:
        def gitignoreFile = targetDir.file(".gitignore")
        gitignoreFile << "*.class${System.lineSeparator()}existingIgnores"
        run('init', '--project-name', 'someApp', '--dsl', scriptDsl.id, '--overwrite')

        then:
        gitignoreFile.file
        gitignoreFile.text == toPlatformLineSeparators("""*.class
existingIgnores
# Ignore Gradle project-specific cache directory
.gradle

# Ignore Gradle build output directory
build
""")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }
}
