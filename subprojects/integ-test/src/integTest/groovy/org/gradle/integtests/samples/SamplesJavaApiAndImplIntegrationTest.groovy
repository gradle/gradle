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
package org.gradle.integtests.samples

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.junit.Rule

class SamplesJavaApiAndImplIntegrationTest extends AbstractIntegrationSpec {

    @Rule public final Sample apiAndImpl = new Sample(temporaryFolder, 'java/apiAndImpl')

    static combined = ""
    static api = "-api"
    static impl = "-impl"

    def "test classpath contains impl and api classes"() {
        given:
        sample apiAndImpl

        when:
        run "test"

        then:
        ":test" in executedTasks
        ":compileApiJava" in executedTasks
        ":compileImplJava" in executedTasks
    }

    def "poms contain the right dependencies"() {
        given:
        sample apiAndImpl

        when:
        run "uploadArchives"

        then: // artifacts published
        module(api).assertArtifactsPublished("apiAndImpl-api-1.0.jar", "apiAndImpl-api-1.0.pom")
        module(impl).assertArtifactsPublished("apiAndImpl-impl-1.0.jar", "apiAndImpl-impl-1.0.pom")
        module(combined).assertArtifactsPublished("apiAndImpl-1.0.jar", "apiAndImpl-1.0.pom")

        and: // poms have the right dependencies
        compileDependenciesOf(api).assertDependsOnArtifacts("commons-codec")
        compileDependenciesOf(impl).assertDependsOnArtifacts("commons-lang")
        compileDependenciesOf(combined).assertDependsOnArtifacts("commons-lang", "commons-codec")

        and: // the fat jar contains classes from api and impl
        jar(combined).file("doubler/Doubler.class").exists()
        jar(combined).file("doubler/impl/DoublerImpl.class").exists()
    }

    def jar(type) {
        def unzipped = apiAndImpl.dir.file("build/unzipped/jar$type")
        if (!unzipped.exists()) {
            artifact(type).unzipTo(unzipped)
        }
        unzipped
    }

    def compileDependenciesOf(type) {
        pom(type).scopes.compile
    }

    def pom(suffix) {
        module(suffix).parsedPom
    }

    def module(suffix) {
        return maven(apiAndImpl.dir.file("build/repo")).module("myorg", "apiAndImpl${suffix}", "1.0")
    }

    def artifact(type) {
        module(type).artifactFile
    }
}