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

import org.gradle.api.Action
import org.gradle.api.internal.filestore.AbstractFileStoreEntry
import org.gradle.api.internal.filestore.FileStore
import org.gradle.api.internal.filestore.FileStoreEntry
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

class GradleBuildOutcomeSetTransformerTest extends Specification {

    def store = new FileStore<String>() {
        FileStoreEntry move(String key, File source) {
            new AbstractFileStoreEntry() {
                File getFile() {
                    source
                }
            }
        }

        FileStoreEntry copy(String key, File source) {
            new AbstractFileStoreEntry() {
                File getFile() {
                    source
                }
            }
        }

        void moveFilestore(File destination) {
            throw new UnsupportedOperationException()
        }

        FileStoreEntry add(String key, Action<File> addAction) {
            throw new UnsupportedOperationException()
        }
    }

    def transformer = new GradleBuildOutcomeSetTransformer(store, "things")
    @Rule TestNameTestDirectoryProvider dir = new TestNameTestDirectoryProvider()

    def "can transform"() {
        given:
        ProjectOutcomesBuilder builder = new ProjectOutcomesBuilder()
        ProjectOutcomes projectOutput = builder.build(dir.testDirectory) {
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
        def outcomes = transformer.transform(projectOutput).collectEntries { [it.name, it] }

        then:
        outcomes.size() == 5
        outcomes.keySet().toList().sort() == [":a:a1", ":b:b1", ":b:b2", ":c:a:ca1", ":c:a:ca2"]
        outcomes[":a:a1"] instanceof GeneratedArchiveBuildOutcome
        outcomes[":a:a1"].archiveFile.name == "a1"
        outcomes[":b:b1"] instanceof GeneratedArchiveBuildOutcome
        outcomes[":b:b1"].archiveFile.name == "b1"
        outcomes[":b:b2"] instanceof GeneratedArchiveBuildOutcome
        outcomes[":c:a:ca1"] instanceof GeneratedArchiveBuildOutcome
        outcomes[":c:a:ca2"] instanceof UnknownBuildOutcome
    }

    List<GradleBuildOutcome> allBuildOutcomes(ProjectOutcomes outcomes, List<GradleBuildOutcome> collector = []) {
        collector.addAll(outcomes.outcomes)
        for (child in outcomes.children) {
            allBuildOutcomes(child, collector)
        }
        collector
    }

}

