/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestEnvironmentPreconditions
import org.junit.Rule

/**
 * Runs the documented sample the way a reader would: the repository it declares is at
 * {@code repo.example.invalid}, which RFC 2606 guarantees cannot resolve, so the dependency only
 * resolves if the mirror in the Maven settings replaces it.
 *
 * <p>The snippet's own check runs without any Maven settings and pins the resulting FAILED report;
 * this is the other half, and needs the network because the sample mirrors to Maven Central.
 */
@Requires(TestEnvironmentPreconditions.Online)
class MavenSettingsMirrorSampleIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @UsesSample("reference/dependency-management/declaring-repositories/maven-settings-mirror")
    def "sample resolves through the mirror declared in the maven settings with the #dsl dsl"() {
        given:
        def projectDir = sample.dir.file(dsl)
        // The docs harness merges common/ into the project; do the same so the sample runs verbatim
        sample.dir.file("common/gradle.properties").copyTo(projectDir.file("gradle.properties"))

        and: "the sample's settings.xml becomes the Maven settings of an isolated home"
        using m2
        executer.beforeExecute m2
        m2.userSettingsFile.text = sample.dir.file("m2_home/settings.xml").text

        when:
        executer.inDirectory(projectDir)
        succeeds 'dependencies', '--configuration', 'runtimeClasspath'

        then: "the mirror stood in for a repository that does not exist"
        outputContains("org.apache.commons:commons-lang3:3.14.0")
        outputDoesNotContain("FAILED")

        where:
        dsl << ['groovy', 'kotlin']
    }
}
