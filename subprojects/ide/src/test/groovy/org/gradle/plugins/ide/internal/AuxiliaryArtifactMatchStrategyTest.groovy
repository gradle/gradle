/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugins.ide.internal

import org.gradle.api.artifacts.result.ArtifactResult
import org.gradle.api.internal.artifacts.result.DefaultResolvedArtifactResult
import org.gradle.language.base.artifact.SourcesArtifact
import org.gradle.plugins.ide.internal.resolver.model.IdeExtendedRepoFileDependency
import spock.lang.Specification


class AuxiliaryArtifactMatchStrategyTest extends Specification {

    final AuxiliaryArtifactMatchStrategy auxiliaryArtifactMatchStrategy = new AuxiliaryArtifactMatchStrategy();
    def foo = new DefaultResolvedArtifactResult(SourcesArtifact.class, new File("foo-1.0.jar"))
    def fooSources = new DefaultResolvedArtifactResult(SourcesArtifact.class, new File("foo-sources-1.0.jar"))
    def fooApiSources = new DefaultResolvedArtifactResult(SourcesArtifact.class, new File("foo-api-sources-1.0.jar"))
    def fooJavadoc = new DefaultResolvedArtifactResult(SourcesArtifact.class, new File("foo-javadoc-1.0.jar"))
    def fooApiJavadoc = new DefaultResolvedArtifactResult(SourcesArtifact.class, new File("foo-api-javadoc-1.0.jar"))

    IdeExtendedRepoFileDependency dependency = Mock()

    def "return nothing when no auxiliary artifacts"() {
        Set<ArtifactResult> artifacts = []

        when:
        dependency.getFile() >> new File("foo-1.0.jar")
        def match = auxiliaryArtifactMatchStrategy.findBestMatch(artifacts, dependency)


        then:
        match == null
    }

    def "best match is single auxiliary artifact when there is only one auxiliary artifact"() {

        Set<ArtifactResult> artifacts = [fooSources] as Set

        when:
        dependency.getFile() >> new File("foo-1.0.jar")
        def match = auxiliaryArtifactMatchStrategy.findBestMatch(artifacts, dependency)

        then:
        match == fooSources
    }

    def "best match is auxiliary artifact with shortest name that starts with artifact name"() {
        Set<ArtifactResult> artifacts = [fooApiSources, fooSources] as Set

        when:
        dependency.getFile() >> new File("foo-1.0.jar")
        def match = auxiliaryArtifactMatchStrategy.findBestMatch(artifacts, dependency)

        then:
        match == fooSources
    }

    def "best javadoc match is auxiliary artifact with shortest name that starts with artifact name"() {
        Set<ArtifactResult> artifacts = [fooApiJavadoc, fooJavadoc] as Set

        when:
        dependency.getFile() >> new File("foo-1.0.jar")
        def match = auxiliaryArtifactMatchStrategy.findBestMatch(artifacts, dependency)

        then:
        match == fooJavadoc
    }

    def "best match when artifact name is longer"() {
        Set<ArtifactResult> artifacts = [fooApiSources, fooSources] as Set

        when:
        dependency.getFile() >> new File("foo-api-1.0.jar")
        def match = auxiliaryArtifactMatchStrategy.findBestMatch(artifacts, dependency)

        then:
        match == fooApiSources
    }

    def "handle source artifact with same filename as main artifact"() {
        Set<ArtifactResult> artifacts = [fooApiSources, foo] as Set

        when:
        dependency.getFile() >> new File("foo-1.0.jar")
        def match = auxiliaryArtifactMatchStrategy.findBestMatch(artifacts, dependency)

        then:
        match == foo
    }
}
