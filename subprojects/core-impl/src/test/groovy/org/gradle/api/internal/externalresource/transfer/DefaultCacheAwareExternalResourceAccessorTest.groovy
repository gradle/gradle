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

package org.gradle.api.internal.externalresource.transfer

import org.gradle.api.internal.externalresource.cached.CachedExternalResourceIndex
import spock.lang.Specification
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceCandidates
import org.gradle.api.internal.externalresource.cached.CachedExternalResource
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData
import org.gradle.util.hash.HashValue
import org.gradle.internal.resource.local.LocallyAvailableResource
import org.gradle.api.internal.externalresource.DefaultLocallyAvailableExternalResource

class DefaultCacheAwareExternalResourceAccessorTest extends Specification {
    final accessor = Mock(ExternalResourceAccessor)
    final index = Mock(CachedExternalResourceIndex)
    final cache = new DefaultCacheAwareExternalResourceAccessor(accessor, index)

    def "will use sha1 from metadata for finding candidates if available"() {
        given:
        def localCandidates = Mock(LocallyAvailableResourceCandidates)
        def cached = Mock(CachedExternalResource)
        def sha1 = HashValue.parse("abc")
        def cachedMetaData = Mock(ExternalResourceMetaData)
        def remoteMetaData = Mock(ExternalResourceMetaData)
        def localCandidate = Mock(LocallyAvailableResource)

        and:
        index.lookup("location") >> cached
        cached.getExternalResourceMetaData() >> cachedMetaData
        accessor.getMetaData("location") >> remoteMetaData
        localCandidates.isNone() >> false
        remoteMetaData.sha1 >> sha1
        
        when:
        def foundResource = cache.getResource("location", localCandidates)

        then:
        0 * accessor.getResourceSha1(_)
        1 * localCandidates.findByHashValue(sha1) >> localCandidate

        and:
        foundResource instanceof DefaultLocallyAvailableExternalResource
    }
}
