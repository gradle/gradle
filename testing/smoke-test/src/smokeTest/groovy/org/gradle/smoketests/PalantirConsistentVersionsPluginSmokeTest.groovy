/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.ToBeImplemented

@Requires(UnitTestPreconditions.Jdk11OrLater)
class PalantirConsistentVersionsPluginSmokeTest extends AbstractSmokeTest {

    /*
      TODO: Until all the commented out deprecations (which became failures in Gradle 9.0) are fixed in a new version of the plugin,
      this test will fail in many, many ways.  We just expect the first one, so that in case a new version of the plugin is released and
      fixes it we'll notice.

> Method call not allowed
    Calling setCanBeConsumed(true) on configuration ':compileClasspathCopy' is not allowed.  This configuration's role was set upon creation and its usage should not be changed.

Called here: https://github.com/tresat/gradle-consistent-versions/blob/develop/src/main/java/com/palantir/gradle/versions/VersionsLockPlugin.java#L662
due to:
https://github.com/tresat/gradle-consistent-versions/blob/23e53fa3b6f7071df8788cecbaeac16f93e90660/src/main/java/com/palantir/gradle/versions/VersionsLockPlugin.java#L640
because of:
https://github.com/tresat/gradle-consistent-versions/blob/23e53fa3b6f7071df8788cecbaeac16f93e90660/src/main/java/com/palantir/gradle/versions/VersionsLockPlugin.java#L609
    */
    @ToBeImplemented("see commented out deprecations - all are not failures in Gradle 9.0")
    def 'basic functionality'() {
        given:
        buildFile << """
            plugins {
                id('java')
                id("com.palantir.consistent-versions") version "${TestedVersions.palantirConsistentVersions}"
            }
            ${mavenCentralRepository()}
        """

        file("settings.gradle") << "include 'other'"
        file("other/build.gradle") << """
            plugins {
                id("java")
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation("com.google.guava:guava")
            }
        """
        file("versions.props") << "com.google.guava:guava = 17.0"

        when:
        SmokeTestGradleRunner.SmokeTestBuildResult result = runner('--write-locks')
            // They are doing some weird stuff in an afterEvaluate
            // See: https://github.com/palantir/gradle-consistent-versions/blob/28a604723c936f5c93c6591e144c4a1731d570ad/src/main/java/com/palantir/gradle/versions/VersionsLockPlugin.java#L277
            // Previously, we expected a ton of deprecation warnings, but with 9.0 this plugin just fails.  Check the history to see them.
            .buildAndFail()
        then:
        result.getOutput().contains("Failed to notify project evaluation listener.")
        result.getOutput().contains("'org.gradle.api.Project org.gradle.api.artifacts.ProjectDependency.getDependencyProject()'")
    }
}
