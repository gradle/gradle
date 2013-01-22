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
import org.gradle.test.fixtures.maven.MavenRepository
import org.junit.Rule

class SigningSamplesSpec extends AbstractIntegrationSpec {
    @Rule public final Sample mavenSample = new Sample(temporaryFolder)

    @UsesSample('signing/maven')
    def "upload attaches signatures"() {
        given:
        sample mavenSample

        when:
        run "uploadArchives"

        then:
        repo.module('gradle', 'maven', '1.0').assertArtifactsPublished('maven-1.0.pom', 'maven-1.0.pom.asc', 'maven-1.0.jar', 'maven-1.0.jar.asc')
    }

    @UsesSample('signing/conditional')
    def "conditional signing"() {
        given:
        sample mavenSample

        when:
        run "uploadArchives"

        then:
        ":signArchives" in skippedTasks

        and:
        repo.module('gradle', 'conditional', '1.0-SNAPSHOT').assertArtifactsPublished('conditional-1.0-SNAPSHOT.pom', 'conditional-1.0-SNAPSHOT.jar')
    }

    MavenRepository getRepo() {
        return maven(mavenSample.dir.file("build/repo"))
    }
}