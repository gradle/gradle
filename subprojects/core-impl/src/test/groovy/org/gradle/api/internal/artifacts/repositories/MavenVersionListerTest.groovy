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
import org.gradle.api.internal.externalresource.ExternalResource
import spock.lang.Specification

class MavenVersionListerTest extends Specification {
    def repo = Mock(ExternalResourceRepository)
    def artifact = Mock(Artifact)
    def moduleRevisionId = ModuleRevisionId.newInstance("org.acme", "testproject", "1.0")

    def repository = Mock(ExternalResourceRepository)
    def root = "localhost:8081/testRepo/"

    def resource = Mock(ExternalResource)
    def resourceInputStream = Mock(InputStream)

    MavenVersionLister lister = new MavenVersionLister(repository, root)

    def "getVersionList throws MissingResourceException when maven-metadata not available"() {
        setup:
        1 * repository.getResource(_) >> null;
        when:
        lister.getVersionList(moduleRevisionId, MavenPattern.M2_PATTERN, artifact)
        then:
        thrown(org.gradle.api.resources.MissingResourceException)
    }

    def "getVersionList throws Exception when maven-metadata cannot be parsed"() {
        setup:
        1 * repository.getResource(_) >> resource;
        1 * resource.openStream() >> resourceInputStream

        when:
        lister.getVersionList(moduleRevisionId, MavenPattern.M2_PATTERN, artifact)
        then:
        thrown(org.gradle.api.internal.resource.ResourceException)
        1 * resource.close()
    }

    def "getVersionList returns emptyList for non M2 compatible pattern"() {
        when:
        def vList = lister.getVersionList(moduleRevisionId, "/non/m2/pattern", artifact)
        then:
        vList.isEmpty()
    }
}
