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
package org.gradle.api.internal.plugins

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.problems.NoOpProblemDiagnosticsFactory
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil
import org.junit.Rule

class DefaultArtifactPublicationSetTest extends AbstractProjectBuilderSpec {
    final CollectingTestOutputEventListener outputEventListener = new CollectingTestOutputEventListener()
    @Rule
    final ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    Configuration aggregateConf
    DefaultArtifactPublicationSet publication

    def setup() {
        aggregateConf = project.configurations.create(Dependency.ARCHIVES_CONFIGURATION)
        publication = TestUtil.newInstance(DefaultArtifactPublicationSet, project.configurations, aggregateConf.name)
    }

    def "adds provider to artifact set"() {
        when:
        publication.addCandidateInternal(artifact(artifactType))

        then:
        aggregateConf.artifacts.size() == 1

        where:
        artifactType << ["jar", "war", "ear", "other"]
    }

    def "adds jar artifact to publication"() {
        def artifact = artifact("jar")

        when:
        publication.addCandidateInternal(artifact)

        then:
        publication.defaultArtifactProvider.get() == set(artifact)
    }

    def "adds war artifact to publication"() {
        def artifact = artifact("war")

        when:
        publication.addCandidateInternal(artifact)

        then:
        publication.defaultArtifactProvider.get() == set(artifact)
    }

    def "adds ear artifact to publication"() {
        def artifact = artifact("ear")

        when:
        publication.addCandidateInternal(artifact)

        then:
        publication.defaultArtifactProvider.get() == set(artifact)
    }

    def "adds other type to publication"() {
        def artifact = artifact("zip")

        when:
        publication.addCandidateInternal(artifact)

        then:
        publication.defaultArtifactProvider.get() == set(artifact)
    }

    def "prefers war over jar artifact"() {
        def jar = artifact("jar")
        def war = artifact("war")

        when:
        publication.addCandidateInternal(jar)

        then:
        publication.defaultArtifactProvider.get() == set(jar)

        when:
        publication.addCandidateInternal(war)

        then:
        publication.defaultArtifactProvider.get() == set(war)

        when:
        publication.addCandidateInternal(jar)

        then:
        publication.defaultArtifactProvider.get() == set(war)
    }

    def "prefers ear over jar artifact"() {
        def jar = artifact("jar")
        def ear = artifact("ear")

        when:
        publication.addCandidateInternal(jar)

        then:
        publication.defaultArtifactProvider.get() == set(jar)

        when:
        publication.addCandidateInternal(ear)

        then:
        publication.defaultArtifactProvider.get() == set(ear)

        when:
        publication.addCandidateInternal(jar)

        then:
        publication.defaultArtifactProvider.get() == set(ear)
    }

    def "prefers ear over war artifact"() {
        def war = artifact("war")
        def ear = artifact("ear")

        when:
        publication.addCandidateInternal(war)

        then:
        publication.defaultArtifactProvider.get() == set(war)

        when:
        publication.addCandidateInternal(ear)

        then:
        publication.defaultArtifactProvider.get() == set(ear)

        when:
        publication.addCandidateInternal(war)

        then:
        publication.defaultArtifactProvider.get() == set(ear)
    }

    def "prefers ear over both jar and war artifacts"() {
        def jar = artifact("jar")
        def war = artifact("war")
        def ear = artifact("ear")

        when:
        publication.addCandidateInternal(jar)

        then:
        publication.defaultArtifactProvider.get() == set(jar)

        when:
        publication.addCandidateInternal(war)

        then:
        publication.defaultArtifactProvider.get() == set(war)

        when:
        publication.addCandidateInternal(ear)

        then:
        publication.defaultArtifactProvider.get() == set(ear)

        when:
        publication.addCandidateInternal(jar)

        then:
        publication.defaultArtifactProvider.get() == set(ear)

        when:
        publication.addCandidateInternal(war)

        then:
        publication.defaultArtifactProvider.get() == set(ear)
    }

    def "always adds other types of artifacts when the default is a #artifactType"() {
        def jarWarEar = artifact(artifactType)
        def exe = artifact("exe")

        when:
        publication.addCandidateInternal(jarWarEar)

        then:
        publication.defaultArtifactProvider.get() == set(jarWarEar)

        when:
        publication.addCandidateInternal(exe)

        then:
        publication.defaultArtifactProvider.get() == set(jarWarEar, exe)

        where:
        artifactType << ["jar", "war", "ear"]
    }

    def "always adds other types of artifacts when the default is not a jar/war/ear"() {
        def zip = artifact("zip")
        def exe = artifact("exe")

        when:
        publication.addCandidateInternal(zip)

        then:
        publication.defaultArtifactProvider.get() == set(zip)

        when:
        publication.addCandidateInternal(exe)

        then:
        publication.defaultArtifactProvider.get() == set(zip, exe)
    }

    def "other artifacts are not removed by jar/war"() {
        def exe = artifact("exe")
        def jar = artifact("jar")
        def war = artifact("war")

        when:
        publication.addCandidateInternal(exe)

        then:
        publication.defaultArtifactProvider.get() == set(exe)

        when:
        publication.addCandidateInternal(jar)

        then:
        publication.defaultArtifactProvider.get() == set(exe)

        when:
        publication.addCandidateInternal(war)

        then:
        publication.defaultArtifactProvider.get() == set(exe)
    }

    def "other artifacts are removed by ear"() {
        def exe = artifact("exe")
        def ear = artifact("ear")

        when:
        publication.addCandidateInternal(exe)

        then:
        publication.defaultArtifactProvider.get() == set(exe)

        when:
        publication.addCandidateInternal(ear)

        then:
        publication.defaultArtifactProvider.get() == set(ear)
    }

    def "current default is cached after realizing the provider"() {
        def jar = Mock(PublishArtifact)
        def war = Mock(PublishArtifact)

        when:
        publication.addCandidateInternal(jar)
        def artifacts = publication.defaultArtifactProvider.get()

        then:
        artifacts == set(jar)
        _ * jar.type >> "jar"

        when:
        publication.addCandidateInternal(war)
        artifacts = publication.defaultArtifactProvider.get()

        then:
        artifacts == set(war)
        _ * jar.type >> "jar"
        _ * war.type >> "war"

        when:
        artifacts = publication.defaultArtifactProvider.get()

        then:
        artifacts == set(war)

        and:
        0 * _
    }

    def "emits deprecation warning when addCandidate is called"() {
        given:
        DeprecationLogger.init(WarningMode.All, Mock(BuildOperationProgressEventEmitter), TestUtil.problemsService(), new NoOpProblemDiagnosticsFactory().newUnlimitedStream())

        when:
        publication.addCandidate(artifact("jar"))

        then:
        def warnings = outputEventListener.events.findAll { it.logLevel == LogLevel.WARN }
        warnings.find {
            it.message == "Internal API DefaultArtifactPublicationSet.addCandidate(PublishArtifact) has been deprecated. This is scheduled to be removed in Gradle 9.0. Add the artifact as a direct dependency of the assemble task instead."
        }

        cleanup:
        DeprecationLogger.reset()
    }

    def PublishArtifact artifact(String type) {
        PublishArtifact artifact = Stub() {
            toString() >> type
            getType() >> type
        }
        return artifact
    }

    Set<PublishArtifact> set(PublishArtifact... artifacts) {
        return artifacts as Set
    }
}
