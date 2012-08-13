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

package org.gradle.api.internal.artifacts.repositories

import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.externalresource.ExternalResource
import org.gradle.api.internal.resource.ResourceNotFoundException
import org.xml.sax.SAXParseException
import spock.lang.Specification
import org.gradle.api.internal.resource.ResourceException

class MavenVersionListerTest extends Specification {
    def repo = Mock(ExternalResourceRepository)
    def artifact = Mock(Artifact)
    def moduleRevisionId = ModuleRevisionId.newInstance("org.acme", "testproject", "1.0")

    def repository = Mock(ExternalResourceRepository)
    def pattern = "localhost:8081/testRepo/" + MavenPattern.M2_PATTERN
    String metaDataResource = 'localhost:8081/testRepo/org.acme/testproject/maven-metadata.xml'

    def resource = Mock(ExternalResource)

    MavenVersionLister lister = new MavenVersionLister(repository)

    def "getVersionList parses maven-metadata.xml"() {
        when:
        def result = lister.getVersionList(moduleRevisionId, pattern, artifact)

        then:
        result.versionStrings == ['1.1', '1.2']
        1 * repository.getResource(metaDataResource) >> resource
        1 * resource.openStream() >> new ByteArrayInputStream("""
<metadata>
    <versioning>
        <versions>
            <version>1.1</version>
            <version>1.2</version>
        </versions>
    </versioning>
</metadata>""".bytes)
    }

    def "getVersionList throws ResourceNotFoundException when maven-metadata not available"() {
        when:
        lister.getVersionList(moduleRevisionId, pattern, artifact)

        then:
        ResourceNotFoundException e = thrown()
        e.message == "Maven meta-data not available: $metaDataResource"
        1 * repository.getResource(metaDataResource) >> null
        0 * repository._
    }

    def "getVersionList throws ResourceException when maven-metadata cannot be parsed"() {
        when:
        lister.getVersionList(moduleRevisionId, pattern, artifact)

        then:
        ResourceException e = thrown()
        e.message == "Unable to load Maven meta-data from $metaDataResource."
        e.cause instanceof SAXParseException
        1 * resource.close()
        1 * repository.getResource(metaDataResource) >> resource;
        1 * resource.openStream() >> new ByteArrayInputStream("yo".bytes)
        0 * repository._
    }

    def "getVersionList throws ResourceException when maven-metadata cannot be loaded"() {
        def failure = new IOException()

        when:
        lister.getVersionList(moduleRevisionId, pattern, artifact)

        then:
        ResourceException e = thrown()
        e.message == "Unable to load Maven meta-data from $metaDataResource."
        e.cause == failure
        1 * repository.getResource(metaDataResource) >> { throw failure }
        0 * repository._
    }

    def "getVersionList throws InvalidUserDataException for non M2 compatible pattern"() {
        when:
        lister.getVersionList(moduleRevisionId, "/non/m2/pattern", artifact)

        then:
        thrown(InvalidUserDataException)
        0 * repository._
    }
}
