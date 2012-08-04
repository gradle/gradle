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
import org.gradle.api.internal.externalresource.cached.CachedExternalResourceIndex
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder
import org.gradle.api.internal.resource.ResourceException
import spock.lang.Specification

class ExternalResourceResolverTest extends Specification {

    def externalResourceRepository = Mock(ExternalResourceRepository)
    def versionLister = Mock(VersionLister)
    def locallyAvailableResourceFinder = Mock(LocallyAvailableResourceFinder)
    def cachedExternalResourceIndex = Mock(CachedExternalResourceIndex)

    def moduleRevidionId = Mock(ModuleRevisionId)
    def pattern = ""
    def artifact = Mock(Artifact)

    ExternalResourceResolver resolver = new ExternalResourceResolver("testresolver", externalResourceRepository, versionLister, locallyAvailableResourceFinder, cachedExternalResourceIndex)

    def "listVersions propagates occured ResourceException"() {
        setup:
        1 * versionLister.getVersionList(moduleRevidionId, pattern, artifact) >> {throw new ResourceException("test exception")}
        when:
        resolver.listVersions(moduleRevidionId, pattern, artifact)
        then:
        thrown(ResourceException)
    }
}
