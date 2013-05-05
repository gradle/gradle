/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.buildcomparison.gradle.internal

import org.gradle.api.internal.filestore.PathNormalisingKeyFileStore
import org.gradle.api.plugins.buildcomparison.fixtures.ProjectOutcomesBuilder
import org.gradle.api.plugins.buildcomparison.outcome.internal.archive.GeneratedArchiveBuildOutcome
import org.gradle.api.plugins.buildcomparison.outcome.internal.unknown.UnknownBuildOutcome
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.model.internal.outcomes.GradleBuildOutcome
import org.gradle.tooling.model.internal.outcomes.GradleFileBuildOutcome
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.plugins.buildcomparison.outcome.internal.FileOutcomeIdentifier.*

class GradleBuildOutcomeSetInferrerTest extends Specification {

    @Rule TestNameTestDirectoryProvider dir = new TestNameTestDirectoryProvider()

    def inferBase = dir.createDir("infer-base")
    def store = new PathNormalisingKeyFileStore(dir.createDir("fs"))
    def transformer = new GradleBuildOutcomeSetTransformer(store, "source")
    def inferrer = new GradleBuildOutcomeSetInferrer(store, "target", inferBase)

    def "can infer"() {
        given:
        ProjectOutcomesBuilder builder = new ProjectOutcomesBuilder()
        ProjectOutcomes projectOutput = builder.build("root", dir.createDir("source")) {
            createChild("a") {
                addFile "a1", JAR_ARTIFACT.typeIdentifier
            }
            createChild("b") {
                addFile "b1", ZIP_ARTIFACT.typeIdentifier
                addFile "b2", EAR_ARTIFACT.typeIdentifier
            }
            createChild("c") {
                createChild("a") {
                    addFile "ca1", WAR_ARTIFACT.typeIdentifier
                    addFile "ca2", TAR_ARTIFACT.typeIdentifier
                }
            }
            createChild("d")
        }

        when:
        allBuildOutcomes(projectOutput).findAll { it instanceof GradleFileBuildOutcome }.each {
            new TestFile(it.file) << it.name
        }
        def outcomes = transformer.transform(projectOutput)

        outcomes.findAll { it instanceof GeneratedArchiveBuildOutcome }.each {
            inferBase.createFile(it.rootRelativePath)
        }

        and:
        def inferred = inferrer.transform(outcomes).collectEntries { [it.name, it] }

        then:
        inferred.size() == 5
        inferred.keySet().toList().sort() == [":a:a1", ":b:b1", ":b:b2", ":c:a:ca1", ":c:a:ca2"]
        inferred[":a:a1"] instanceof GeneratedArchiveBuildOutcome
        inferred[":a:a1"].archiveFile.name == "a1"
        inferred[":b:b1"] instanceof GeneratedArchiveBuildOutcome
        inferred[":b:b1"].archiveFile.name == "b1"
        inferred[":b:b2"] instanceof GeneratedArchiveBuildOutcome
        inferred[":c:a:ca1"] instanceof GeneratedArchiveBuildOutcome
        inferred[":c:a:ca2"] instanceof UnknownBuildOutcome
    }

    List<GradleBuildOutcome> allBuildOutcomes(ProjectOutcomes outcomes, List<GradleBuildOutcome> collector = []) {
        collector.addAll(outcomes.outcomes)
        for (child in outcomes.children) {
            allBuildOutcomes(child, collector)
        }
        collector
    }

}
