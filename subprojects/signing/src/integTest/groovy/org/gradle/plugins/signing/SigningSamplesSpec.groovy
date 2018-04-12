/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.plugins.signing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.util.Requires
import org.junit.Rule
import spock.lang.IgnoreIf

class SigningSamplesSpec extends AbstractIntegrationSpec {
    @Rule
    public final Sample sampleProject = new Sample(temporaryFolder)

    void setup() {
        using m2
    }

    @UsesSample('signing/maven')
    def "upload attaches signatures"() {
        given:
        sample sampleProject

        when:
        run "uploadArchives"

        then:
        repo.module('gradle', 'maven', '1.0').assertArtifactsPublished('maven-1.0.pom', 'maven-1.0.pom.asc', 'maven-1.0.jar', 'maven-1.0.jar.asc')
    }

    @UsesSample('signing/conditional')
    @IgnoreIf({ GradleContextualExecuter.parallel })
    def "conditional signing"() {
        given:
        sample sampleProject

        when:
        run "uploadArchives"

        then:
        ":signArchives" in skippedTasks

        and:
        final module = repo.module('gradle', 'conditional', '1.0-SNAPSHOT')
        module.assertArtifactsPublished("maven-metadata.xml", "conditional-${module.publishArtifactVersion}.pom", "conditional-${module.publishArtifactVersion}.jar")
    }

    @UsesSample('signing/gnupg-signatory')
    @Requires(adhoc = { GpgCmdFixture.getAvailableGpg() != null })
    def "use gnupg signatory"() {
        setup:
        def symlink = GpgCmdFixture.setupGpgCmd(file('signing/gnupg-signatory'))

        when:
        sample sampleProject

        and:
        run "signArchives"

        then:
        file("signing", "gnupg-signatory", "build", "libs", "gnupg-signatory-1.0.jar.asc").assertExists()

        cleanup:
        GpgCmdFixture.cleanupGpgCmd(symlink)
    }

    @UsesSample('signing/maven-publish')
    def "publish attaches signatures"() {
        given:
        sample sampleProject

        when:
        run "publish"

        then:
        repo.module('gradle', 'maven-publish', '1.0').assertArtifactsPublished('maven-publish-1.0.pom', 'maven-publish-1.0.pom.asc', 'maven-publish-1.0.jar', 'maven-publish-1.0.jar.asc')
    }

    MavenFileRepository getRepo() {
        return maven(sampleProject.dir.file("build/repo"))
    }
}
