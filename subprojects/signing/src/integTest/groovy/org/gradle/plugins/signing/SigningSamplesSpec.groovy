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

        and:
        def artifactId = "my-library"
        def version = "1.0"
        def fileRepo = maven(sampleProject.dir.file("build/repos/releases"))
        def module = fileRepo.module("com.example", artifactId, version)

        when:
        succeeds "publish"

        then:
        module.assertPublished()
        def expectedFileNames = ["${artifactId}-${version}.jar", "${artifactId}-${version}-sources.jar", "${artifactId}-${version}-javadoc.jar", "${artifactId}-${version}.pom"]
        module.assertArtifactsPublished(expectedFileNames.collect { [it, "${it}.asc"] }.flatten())

        and:
        module.parsedPom.name == "My Library"
        module.parsedPom.description == "A concise description of my library"
        module.parsedPom.url == "http://www.example.com/library"
        module.parsedPom.licenses[0].name.text() == "The Apache License, Version 2.0"
        module.parsedPom.licenses[0].url.text() == "http://www.apache.org/licenses/LICENSE-2.0.txt"
        module.parsedPom.developers[0].id.text() == "johnd"
        module.parsedPom.developers[0].name.text() == "John Doe"
        module.parsedPom.developers[0].email.text() == "john.doe@example.com"
        module.parsedPom.scm.connection.text() == 'scm:git:git://example.com/my-library.git'
        module.parsedPom.scm.developerConnection.text() == 'scm:git:ssh://example.com/my-library.git'
        module.parsedPom.scm.url.text() == 'http://example.com/my-library/'
    }

    MavenFileRepository getRepo() {
        return maven(sampleProject.dir.file("build/repo"))
    }
}
