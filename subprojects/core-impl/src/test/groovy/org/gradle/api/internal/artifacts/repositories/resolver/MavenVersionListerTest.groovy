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

package org.gradle.api.internal.artifacts.repositories.resolver

import org.apache.ivy.core.module.descriptor.DefaultArtifact
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.Action
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ExactVersionMatcher
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.LatestVersionStrategy
import org.gradle.api.internal.externalresource.ExternalResource
import org.gradle.api.internal.externalresource.transport.ExternalResourceRepository
import org.gradle.api.internal.resource.ResourceException
import org.gradle.api.internal.resource.ResourceNotFoundException
import org.gradle.internal.UncheckedException
import org.xml.sax.SAXParseException
import spock.lang.Specification

class MavenVersionListerTest extends Specification {
    def repo = Mock(ExternalResourceRepository)
    def moduleRevisionId = ModuleRevisionId.newInstance("org.acme", "testproject", "1.0")
    def artifact = new DefaultArtifact(moduleRevisionId, new Date(), "testproject", "jar", "jar")

    def repository = Mock(ExternalResourceRepository)
    def pattern = pattern("localhost:8081/testRepo/" + MavenPattern.M2_PATTERN)
    String metaDataResource = 'localhost:8081/testRepo/org/acme/testproject/maven-metadata.xml'

    final MavenVersionLister lister = new MavenVersionLister(repository)

    def "visit parses maven-metadata.xml"() {
        ExternalResource resource = Mock()

        when:
        def versionList = lister.getVersionList(moduleRevisionId)
        versionList.visit(pattern, artifact)

        then:
        sort(versionList).collect {it.version} == ['1.2', '1.1']

        and:
        1 * repository.getResource(metaDataResource) >> resource
        1 * resource.withContent(_) >> { Action action -> action.execute(new ByteArrayInputStream("""
<metadata>
    <versioning>
        <versions>
            <version>1.1</version>
            <version>1.2</version>
        </versions>
    </versioning>
</metadata>""".bytes))
        }
        1 * resource.close()
        0 * repository._
        0 * resource._
    }

    def "visit builds union of versions"() {
        ExternalResource resource1 = Mock()
        ExternalResource resource2 = Mock()

        when:
        def versionList = lister.getVersionList(moduleRevisionId)
        final pattern1 = pattern("prefix1/" + MavenPattern.M2_PATTERN)
        versionList.visit(pattern1, artifact)
        final pattern2 = pattern("prefix2/" + MavenPattern.M2_PATTERN)
        versionList.visit(pattern2, artifact)

        then:
        sort(versionList).collect {it.version} == ['1.3', '1.2', '1.1']
        sort(versionList).collect {it.pattern} == [pattern2, pattern1, pattern1]

        and:
        1 * repository.getResource('prefix1/org/acme/testproject/maven-metadata.xml') >> resource1
        1 * resource1.withContent(_) >> { Action action -> action.execute(new ByteArrayInputStream("""
<metadata>
    <versioning>
        <versions>
            <version>1.1</version>
            <version>1.2</version>
        </versions>
    </versioning>
</metadata>""".bytes))
        }
        1 * repository.getResource('prefix2/org/acme/testproject/maven-metadata.xml') >> resource2
        1 * resource2.withContent(_) >> { Action action -> action.execute(new ByteArrayInputStream("""
<metadata>
    <versioning>
        <versions>
            <version>1.2</version>
            <version>1.3</version>
        </versions>
    </versioning>
</metadata>""".bytes))
        }
    }

    def "visit ignores duplicate patterns"() {
        ExternalResource resource = Mock()

        when:
        def versionList = lister.getVersionList(moduleRevisionId)
        versionList.visit(pattern, artifact)
        versionList.visit(pattern, artifact)

        then:
        sort(versionList).collect {it.version} == ['1.2', '1.1']

        and:
        1 * repository.getResource(metaDataResource) >> resource
        1 * resource.withContent(_) >> { Action action -> action.execute(new ByteArrayInputStream("""
<metadata>
    <versioning>
        <versions>
            <version>1.1</version>
            <version>1.2</version>
        </versions>
    </versioning>
</metadata>""".bytes))
        }
        1 * resource.close()
        0 * repository._
        0 * resource._
    }

    private static List<VersionList.ListedVersion> sort(VersionList versionList) {
        def latestStrategy = new LatestVersionStrategy()
        latestStrategy.versionMatcher = new ExactVersionMatcher()
        versionList.sortLatestFirst(latestStrategy)
    }

    def "visit throws ResourceNotFoundException when maven-metadata not available"() {
        when:
        def versionList = lister.getVersionList(moduleRevisionId)
        versionList.visit(pattern, artifact)

        then:
        ResourceNotFoundException e = thrown()
        e.message == "Maven meta-data not available: $metaDataResource"
        1 * repository.getResource(metaDataResource) >> null
        0 * repository._
    }

    def "visit throws ResourceException when maven-metadata cannot be parsed"() {
        ExternalResource resource = Mock()

        when:
        def versionList = lister.getVersionList(moduleRevisionId)
        versionList.visit(pattern, artifact)

        then:
        ResourceException e = thrown()
        e.message == "Unable to load Maven meta-data from $metaDataResource."
        e.cause instanceof UncheckedException
        e.cause.cause instanceof SAXParseException
        1 * resource.close()
        1 * repository.getResource(metaDataResource) >> resource;
        1 * resource.withContent(_) >> { Action action -> action.execute(new ByteArrayInputStream("yo".bytes)) }
        0 * repository._
    }

    def "visit throws ResourceException when maven-metadata cannot be loaded"() {
        def failure = new IOException()

        when:
        def versionList = lister.getVersionList(moduleRevisionId)
        versionList.visit(pattern, artifact)

        then:
        ResourceException e = thrown()
        e.message == "Unable to load Maven meta-data from $metaDataResource."
        e.cause == failure
        1 * repository.getResource(metaDataResource) >> { throw failure }
        0 * repository._
    }

    def pattern(String pattern) {
        return new M2ResourcePattern(pattern)
    }
}
