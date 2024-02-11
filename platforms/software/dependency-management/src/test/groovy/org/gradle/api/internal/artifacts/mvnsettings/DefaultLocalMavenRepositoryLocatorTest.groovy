/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.artifacts.mvnsettings

import org.apache.maven.settings.io.SettingsParseException
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

class DefaultLocalMavenRepositoryLocatorTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    SimpleMavenFileLocations locations
    DefaultLocalMavenRepositoryLocator locator

    def system = Mock(DefaultLocalMavenRepositoryLocator.SystemPropertyAccess)
    def mavenFileLocations = Mock(MavenFileLocations)

    File repo1 = tmpDir.file("repo1")
    File repo2 = tmpDir.file("repo2")
    File userHome1 = tmpDir.file("user_home_1")
    File defaultM2Repo = new File(userHome1, ".m2/repository")

    def setup() {
        locations = new SimpleMavenFileLocations()
        locator = new DefaultLocalMavenRepositoryLocator(new DefaultMavenSettingsProvider(locations), mavenFileLocations, system)
    }

    def "returns default location if no settings file exists"() {
        when:
        1 * mavenFileLocations.getUserMavenDir() >> new File(userHome1, ".m2")
        then:
        locator.localMavenRepository == defaultM2Repo

        // Ensure that modified user.home is honoured (see http://forums.gradle.org/gradle/topics/override_location_of_the_local_maven_repo)
        when:
        File userHome2 = tmpDir.file("user_home_2")
        1 * mavenFileLocations.getUserMavenDir() >> new File(userHome2, ".m2")
        then:
        locator.localMavenRepository == new File(userHome2, ".m2/repository")
    }

    def "returns value of system property if it is specified"() {
        when:
        1 * system.getProperty("maven.repo.local") >> repo1.absolutePath
        then:
        locator.localMavenRepository == repo1

        // Ensure that modified system property is honoured
        when:
        1 * system.getProperty("maven.repo.local") >> repo2.absolutePath
        then:
        locator.localMavenRepository == repo2
    }

    def "throws exception on broken global settings file with decent error message"() {
        given:
        def settingsFile = locations.globalSettingsFile
        settingsFile << "broken content"
        when:
        locator.localMavenRepository
        then:
        def ex = thrown(CannotLocateLocalMavenRepositoryException);
        ex.message == "Unable to parse local Maven settings: " + settingsFile.absolutePath
        ex.cause instanceof SettingsParseException
    }

    def "throws exception on broken user settings file with decent error message"() {
        given:
        def settingsFile = locations.userSettingsFile
        settingsFile << "broken content"
        when:
        locator.localMavenRepository
        then:
        def ex = thrown(CannotLocateLocalMavenRepositoryException)
        ex.message == "Unable to parse local Maven settings: " + settingsFile.absolutePath
        ex.cause instanceof SettingsParseException
    }

    def "honors location specified in user settings file"() {
        writeSettingsFile(locations.userSettingsFile, repo1)

        expect:
        locator.localMavenRepository == repo1
    }

    def "honors location specified in global settings file"() {
        writeSettingsFile(locations.globalSettingsFile, repo1)

        expect:
        locator.localMavenRepository == repo1
    }

    def "returns default location if user settings file specifies empty local repository"() {
        when:
        writeSettingsFile(locations.userSettingsFile, localRepository)
        1 * mavenFileLocations.getUserMavenDir() >> new File(userHome1, ".m2")

        then:
        locator.localMavenRepository == defaultM2Repo

        where:
        localRepository << ["<localRepository/>", "<localRepository></localRepository>", ""]
    }

    def "returns default location if global settings file specifies empty local repository"() {
        when:
        writeSettingsFile(locations.globalSettingsFile, localRepository)
        1 * mavenFileLocations.getUserMavenDir() >> new File(userHome1, ".m2")

        then:
        locator.localMavenRepository == defaultM2Repo

        where:
        localRepository << ["<localRepository/>", "<localRepository></localRepository>", ""]
    }

    def "returns global location if user settings file specifies empty local repository"() {
        when:
        writeSettingsFile(locations.userSettingsFile, localRepository)
        writeSettingsFile(locations.globalSettingsFile, repo2)

        then:
        locator.localMavenRepository == repo2

        where:
        localRepository << ["<localRepository/>", "<localRepository></localRepository>", ""]
    }

    def "prefers location specified in user settings file over that in global settings file"() {
        writeSettingsFile(locations.userSettingsFile, repo1)
        writeSettingsFile(locations.globalSettingsFile, repo2)

        expect:
        locator.localMavenRepository == repo1
    }

    def "ignores changes to maven settings file after initial load"() {
        when:
        writeSettingsFile(locations.userSettingsFile, repo1)

        then:
        locator.localMavenRepository == repo1

        when:
        writeSettingsFile(locations.userSettingsFile, repo2)

        then:
        locator.localMavenRepository == repo1
    }

    def "handles the case where (potential) location of global settings file cannot be determined"() {
        locations.globalSettingsFile = null

        when:
        mavenFileLocations.getUserMavenDir() >> new File(userHome1, ".m2")

        then:
        locator.localMavenRepository == defaultM2Repo

        when:
        writeSettingsFile(locations.userSettingsFile, repo1)

        then:
        locator.localMavenRepository == repo1
    }

    def "replaces placeholders for system properties and environment variables"() {
        when:
        writeSettingsFile(locations.userSettingsFile, tmpDir.file('${sys.prop}/${env.ENV_VAR}'))

        and:
        system.getProperty("sys.prop") >> "sys/prop/value"
        system.getEnv("ENV_VAR") >> "env/var/value"

        then:
        locator.localMavenRepository == tmpDir.file("sys/prop/value/env/var/value")
    }

    def "unresolvable placeholder for #propType throws exception with decent error message"() {
        TestFile repoPath = tmpDir.file("\${$prop}")
        writeSettingsFile(locations.userSettingsFile, repoPath)
        when:
        locator.localMavenRepository
        then:
        def ex = thrown(CannotLocateLocalMavenRepositoryException);
        ex.message == "Cannot resolve placeholder '${prop}' in value '${repoPath.absolutePath}'"
        where:
        prop                  |   propType
        'sys.unknown.prop'    |   "system property"
        'env.unknown.ENV_VAR' |   "environment variable"
    }

    @Requires(UnitTestPreconditions.Windows)
    @Issue('https://github.com/gradle/gradle/issues/2843')
    def "handle the case of absolute path on Windows"() {
        when:
        writeSettingsFile(locations.globalSettingsFile, "<localRepository>/absolute</localRepository>")
        then:
        locator.localMavenRepository == new File('/absolute').absoluteFile
    }

    private static void writeSettingsFile(File settings, File repo) {
        writeSettingsFile(settings, "<localRepository>${repo.absolutePath}</localRepository>")
    }

    private static void writeSettingsFile(File settings, String localRepositoryElement) {
        settings.text = """
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  $localRepositoryElement
</settings>"""
    }

    private class SimpleMavenFileLocations implements MavenFileLocations {
        File userMavenDir
        File globalMavenDir
        File userSettingsFile = tmpDir.file("userSettingsFile")
        File globalSettingsFile = tmpDir.file("globalSettingsFile")
    }
}
