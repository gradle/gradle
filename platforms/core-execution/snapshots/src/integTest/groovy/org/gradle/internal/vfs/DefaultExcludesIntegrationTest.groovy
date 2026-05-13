/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.vfs

import org.apache.tools.ant.DirectoryScanner
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.Issue

class DefaultExcludesIntegrationTest extends AbstractIntegrationSpec{

    private static final EXCLUDED_FILE_NAME = "my-excluded-file.txt"
    private static final DIRECTORY_SCANNER_DEPRECATION =
        "Mutating org.apache.tools.ant.DirectoryScanner default excludes has been deprecated. " +
        "This is scheduled to be removed in Gradle 10. " +
        "Use settings.fileSystemDefaultExcludes in settings.gradle(.kts) instead. " +
        "For example: fileSystemDefaultExcludes.add(\"**/node_modules\"). " +
        "Consult the upgrading guide for further information: " +
        "https://docs.gradle.org/current/userguide/upgrading_version_9.html#directoryscanner_default_excludes_deprecation"
    private static final DIRECTORY_SCANNER_AND_SETTINGS_DEPRECATION =
        "Configuring file-system default excludes via both " +
        "org.apache.tools.ant.DirectoryScanner and settings.fileSystemDefaultExcludes has been deprecated. " +
        "This is scheduled to be removed in Gradle 10. " +
        "settings.fileSystemDefaultExcludes takes precedence; the DirectoryScanner mutation is ignored. " +
        "Remove the DirectoryScanner calls from your settings script. " +
        "Consult the upgrading guide for further information: " +
        "https://docs.gradle.org/current/userguide/upgrading_version_9.html#directoryscanner_default_excludes_deprecation"
    private static final DEFAULT_EXCLUDES = [
        "**/%*%",
        "**/.#*",
        "**/._*",
        "**/#*#",
        "**/*~",
        "**/.DS_Store",

        "**/CVS",
        "**/CVS/**",
        "**/.cvsignore",

        "**/SCCS",
        "**/SCCS/**",

        "**/.bzr",
        "**/.bzr/**",
        "**/.bzrignore",

        "**/vssver.scc",

        "**/.hg",
        "**/.hg/**",
        "**/.hgtags",
        "**/.hgignore",
        "**/.hgsubstate",
        "**/.hgsub",

        "**/.svn",
        "**/.svn/**",

        "**/.git",
        "**/.git/**",
        "**/.gitignore",
        "**/.gitmodules",
        "**/.gitattributes"
    ]

    def outputDir = file("build/output")
    def excludedFile = file("input/${EXCLUDED_FILE_NAME}")
    def copyOfExcludedFile = outputDir.file(EXCLUDED_FILE_NAME)

    def setup() {
        file("input/inputFile.txt").text = "input"
        excludedFile.text = "excluded"
        buildFile << """
            task copyTask(type: Copy) {
                from("input")
                into("build/output")
            }
        """
    }

    def "default excludes defined in settings.gradle are used"() {
        settingsFile << addDefaultExclude(EXCLUDED_FILE_NAME)

        def outputDir = file("build/output")
        file("input/inputFile.txt").text = "input"
        def excludedFile = file("input/${EXCLUDED_FILE_NAME}")
        excludedFile.text = "initial"
        def copyOfExcludedFile = outputDir.file(EXCLUDED_FILE_NAME)

        when:
        executer.expectDocumentedDeprecationWarning(DIRECTORY_SCANNER_DEPRECATION)
        run "copyTask"
        then:
        executedAndNotSkipped(":copyTask")
        !copyOfExcludedFile.exists()

        when:
        excludedFile.text = "changed"
        if (!GradleContextualExecuter.configCache) {
            // settingsEvaluated re-runs on every non-CC build, so the deprecation re-fires.
            // On a CC hit, settings come from cache and the user's DirectoryScanner mutation is not re-executed.
            executer.expectDocumentedDeprecationWarning(DIRECTORY_SCANNER_DEPRECATION)
        }
        run "copyTask"
        then:
        skipped(":copyTask")
    }

    def "default excludes are reset if nothing is defined in settings"() {
        settingsFile << addDefaultExclude(EXCLUDED_FILE_NAME)

        when:
        executer.expectDocumentedDeprecationWarning(DIRECTORY_SCANNER_DEPRECATION)
        run "copyTask"
        then:
        executedAndNotSkipped(":copyTask")
        !copyOfExcludedFile.exists()

        when:
        excludedFile.text = "changed"
        if (!GradleContextualExecuter.configCache) {
            executer.expectDocumentedDeprecationWarning(DIRECTORY_SCANNER_DEPRECATION)
        }
        run "copyTask"
        then:
        skipped(":copyTask")

        when:
        settingsFile.text = ""
        excludedFile.text = "changedAgain"
        run "copyTask"
        then:
        executedAndNotSkipped(":copyTask")
        copyOfExcludedFile.exists()
    }

    def "fails when default excludes are changed during the build"() {
        settingsFile << addDefaultExclude(EXCLUDED_FILE_NAME)

        buildFile << """
            copyTask.doFirst {
                ant.defaultexcludes remove: '**/${EXCLUDED_FILE_NAME}'
            }
            copyTask.doLast {
                ant.defaultexcludes default: true
            }
        """
        List<String> defaultExcludesFromSettings = (DEFAULT_EXCLUDES  + ['**/' + EXCLUDED_FILE_NAME]).toSorted()
        List<String> defaultExcludesInTask = DEFAULT_EXCLUDES.toSorted()

        when:
        executer.expectDocumentedDeprecationWarning(DIRECTORY_SCANNER_DEPRECATION)
        fails "copyTask"

        then:
        failure.assertHasCause "Cannot change default excludes during the build. They were changed from ${defaultExcludesFromSettings} to ${defaultExcludesInTask}. Configure default excludes in the settings script instead."
    }

    @Issue("https://github.com/gradle/gradle/issues/27225")
    def "default excludes are removed properly"() {
        def defaultExclude = '.gitignore'
        def defaultExcludeFile = file("input/$defaultExclude")
        defaultExcludeFile << "some content"

        settingsFile << removeDefaultExclude(defaultExclude)

        when:
        executer.expectDocumentedDeprecationWarning(DIRECTORY_SCANNER_DEPRECATION)
        run "copyTask"
        then:
        executedAndNotSkipped(":copyTask")
        file("build/output/$defaultExclude").exists()

        when:
        defaultExcludeFile.text = "changed"
        if (!GradleContextualExecuter.configCache) {
            executer.expectDocumentedDeprecationWarning(DIRECTORY_SCANNER_DEPRECATION)
        }
        run "copyTask"
        then:
        executedAndNotSkipped(":copyTask")
    }

    def "default excludes defined via settings.fileSystemDefaultExcludes are used"() {
        settingsFile << """
            fileSystemDefaultExcludes.add('**/${EXCLUDED_FILE_NAME}')
        """

        when:
        run "copyTask"
        then:
        executedAndNotSkipped(":copyTask")
        !copyOfExcludedFile.exists()

        when:
        excludedFile.text = "changed"
        run "copyTask"
        then:
        skipped(":copyTask")
    }

    def "settings.fileSystemDefaultExcludes can remove a built-in default exclude"() {
        def defaultExclude = '.gitignore'
        def defaultExcludeFile = file("input/$defaultExclude")
        defaultExcludeFile << "some content"

        settingsFile << """
            fileSystemDefaultExcludes.set(fileSystemDefaultExcludes.get() - '**/${defaultExclude}')
        """

        when:
        run "copyTask"
        then:
        executedAndNotSkipped(":copyTask")
        file("build/output/$defaultExclude").exists()

        when:
        defaultExcludeFile.text = "changed"
        run "copyTask"
        then:
        executedAndNotSkipped(":copyTask")
    }

    def "settings.fileSystemDefaultExcludes wins when both APIs configure the defaults"() {
        // The new Settings API takes precedence; mutations to the legacy DirectoryScanner static state are ignored.
        settingsFile << """
            ${DirectoryScanner.name}.addDefaultExclude('**/legacy-excluded.txt')
            fileSystemDefaultExcludes.add('**/${EXCLUDED_FILE_NAME}')
        """
        file('input/legacy-excluded.txt').text = "from legacy API"

        when:
        executer.expectDocumentedDeprecationWarning(DIRECTORY_SCANNER_AND_SETTINGS_DEPRECATION)
        run "copyTask"
        then:
        executedAndNotSkipped(":copyTask")
        !copyOfExcludedFile.exists() // new API exclude applied
        outputDir.file('legacy-excluded.txt').exists() // legacy mutation ignored
    }

    private static String addDefaultExclude(String excludedFileName = EXCLUDED_FILE_NAME) {
        """
            ${DirectoryScanner.name}.addDefaultExclude('**/${ excludedFileName}')
        """
    }

    private static String removeDefaultExclude(String defaultExcludedFileName) {
        """
            ${DirectoryScanner.name}.removeDefaultExclude('**/${defaultExcludedFileName}')
        """
    }
}
