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


class ShortestNameAuxiliaryArtifactMatchStrategyTest extends Specification {

    final ShortestNameAuxiliaryArtifactMatchStrategy strategy = new ShortestNameAuxiliaryArtifactMatchStrategy();

    def foo = new DefaultResolvedArtifactResult(SourcesArtifact.class, new File("foo-1.0.jar"))
    def fooSources = new DefaultResolvedArtifactResult(SourcesArtifact.class, new File("foo-sources-1.0.jar"))
    def fooApiSources = new DefaultResolvedArtifactResult(SourcesArtifact.class, new File("foo-api-sources-1.0.jar"))
    def fooJavadoc = new DefaultResolvedArtifactResult(SourcesArtifact.class, new File("foo-javadoc-1.0.jar"))
    def fooApiJavadoc = new DefaultResolvedArtifactResult(SourcesArtifact.class, new File("foo-api-javadoc-1.0.jar"))

    IdeExtendedRepoFileDependency dependency = Mock()

    def "return nothing when no auxiliary artifacts"() {
        when:
        dependency.getFile() >> new File("foo-1.0.jar")
        then:
        strategy.findBestMatch([] as Set, dependency) == null
    }

    def "best match is single auxiliary artifact when there is only one auxiliary artifact"() {
        when:
        Set<ArtifactResult> artifacts = [fooSources] as Set
        dependency.getFile() >> new File("foo-1.0.jar")

        then:
        strategy.findBestMatch(artifacts, dependency) == fooSources
    }

    def "best match is auxiliary artifact with shortest name that starts with artifact name"() {
        when:
        Set<ArtifactResult> artifacts = [fooApiSources, fooSources] as Set
        dependency.getFile() >> new File("foo-1.0.jar")

        then:
        strategy.findBestMatch(artifacts, dependency) == fooSources
    }

    def "best javadoc match is auxiliary artifact with shortest name that starts with artifact name"() {
        when:
        Set<ArtifactResult> artifacts = [fooApiJavadoc, fooJavadoc] as Set
        dependency.getFile() >> new File("foo-1.0.jar")
        then:
        strategy.findBestMatch(artifacts, dependency) == fooJavadoc
    }

    def "best match when artifact name is longer"() {
        when:
        Set<ArtifactResult> artifacts = [fooApiSources, fooSources] as Set
        dependency.getFile() >> new File("foo-api-1.0.jar")

        then:
        strategy.findBestMatch(artifacts, dependency)  == fooApiSources
    }

    def "handle source artifact with same filename as main artifact"() {
        when:
        Set<ArtifactResult> artifacts = [fooApiSources, foo] as Set
        dependency.getFile() >> new File("foo-1.0.jar")

        then:
        strategy.findBestMatch(artifacts, dependency) == foo
    }
}
