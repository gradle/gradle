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
package org.gradle.integtests

import org.gradle.integtests.fixtures.*
import org.gradle.integtests.fixtures.internal.*

import org.junit.Rule
import spock.lang.*

class SamplesJavaApiAndImplIntegrationTest extends AbstractIntegrationSpec {

    @Rule public final Sample apiAndImpl = new Sample('java/apiAndImpl')

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

        then: // poms have the right dependencies
        compileArtifactIdsOf(api) == ["commons-codec"]
        compileArtifactIdsOf(impl) == ["commons-lang"]
        compileArtifactIdsOf(combined) == ["commons-codec", "commons-lang"]

        and: // the fat jar contains classes from api and impl
        jar(combined).file("doubler/Doubler.class").exists()
        jar(combined).file("doubler/impl/DoublerImpl.class").exists()
    }

    def jar(type) {
        def unzipped = apiAndImpl.dir.file("build/unzipped/jar$type")
        if (!unzipped.exists()) {
            artifact(type, "jar").unzipTo(unzipped)
        }
        unzipped
    }

    def compileArtifactIdsOf(type) {
        compileDependenciesOf(type).collect { it.artifactId[0].text() }
    }

    def compileDependenciesOf(type) {
        pom(type).dependencies.dependency.findAll { it.scope[0] == "compile" }
    }

    def pom(suffix) {
        new XmlSlurper().parse(artifact(suffix, "pom"))
    }

    def artifact(type, ext) {
        apiAndImpl.dir.file("build/repo/myorg/apiAndImpl${type}/1.0/apiAndImpl${type}-1.0.${ext}")
    }
}