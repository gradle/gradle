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

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.util.Requires
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Unroll

import static org.gradle.util.TestPrecondition.KOTLIN_SCRIPT

@Requires(KOTLIN_SCRIPT)
class SigningSamplesSpec extends AbstractSampleIntegrationTest {
    @Rule
    public final Sample sample = new Sample(testDirectoryProvider)

    def setup() {
        using m2
        requireGradleDistribution()
    }

    @Unroll
    @UsesSample('signing/maven')
    def "upload attaches signatures with dsl #dsl"() {
        given:
        inDirectory(sample.dir.file(dsl))

        when:
        run "uploadArchives"

        then:
        repoFor(dsl)
            .module('gradle', 'maven', '1.0')
            .assertArtifactsPublished('maven-1.0.pom', 'maven-1.0.pom.asc', 'maven-1.0.jar', 'maven-1.0.jar.asc')

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample('signing/conditional')
    @IgnoreIf({ GradleContextualExecuter.parallel })
    def "conditional signing with dsl #dsl"() {
        given:
        inDirectory(sample.dir.file(dsl))

        when:
        run "uploadArchives"

        then:
        ":signArchives" in skippedTasks

        and:
        def module = repoFor(dsl).module('gradle', 'conditional', '1.0-SNAPSHOT')
        module.assertArtifactsPublished("maven-metadata.xml", "conditional-${module.publishArtifactVersion}.pom", "conditional-${module.publishArtifactVersion}.jar")

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample('signing/gnupg-signatory')
    @Requires(adhoc = { GpgCmdFixture.getAvailableGpg() != null })
    def "use gnupg signatory with dsl #dsl"() {
        setup:
        def projectDir = sample.dir.file(dsl)
        def symlink = GpgCmdFixture.setupGpgCmd(projectDir)

        when:
        inDirectory(projectDir)

        and:
        run "signArchives"

        then:
        projectDir.file("build/libs/gnupg-signatory-1.0.jar.asc").assertExists()

        cleanup:
        GpgCmdFixture.cleanupGpgCmd(symlink)

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample('signing/maven-publish')
    def "publish attaches signatures with dsl #dsl"() {
        given:
        inDirectory(sample.dir.file(dsl))

        and:
        def artifactId = "my-library"
        def version = "1.0"
        def fileRepo = maven(sample.dir.file("$dsl/build/repos/releases"))
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

        where:
        dsl << ['groovy', 'kotlin']
    }

    MavenFileRepository repoFor(String dsl) {
        return maven(sample.dir.file("$dsl/build/repo"))
    }
}
