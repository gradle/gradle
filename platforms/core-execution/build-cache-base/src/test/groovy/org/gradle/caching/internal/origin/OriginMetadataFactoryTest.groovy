/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.caching.internal.origin

import org.gradle.caching.internal.CacheableEntity
import org.gradle.internal.hash.TestHashCodes
import spock.lang.Specification

import java.time.Duration

class OriginMetadataFactoryTest extends Specification {
    def buildInvocationId = UUID.randomUUID().toString()
    def factory = new OriginMetadataFactory(
        buildInvocationId,
        { it.gradleVersion = "3.0" }
    )
    def buildCacheKey = TestHashCodes.hashCodeFrom(1234)

    def "converts to origin metadata"() {
        def origin = new Properties()
        def writer = factory.createWriter("identity", CacheableEntity, buildCacheKey, Duration.ofMillis(10))
        def baos = new ByteArrayOutputStream()
        writer.execute(baos)
        when:
        def reader = factory.createReader()
        // doesn't explode
        reader.execute(new ByteArrayInputStream(baos.toByteArray()))
        and:
        origin.load(new ByteArrayInputStream(baos.toByteArray()))
        then:
        origin.identity == "identity"
        origin.type == CacheableEntity.canonicalName
        origin.buildCacheKey == buildCacheKey.toString()
        origin.gradleVersion == "3.0"
        origin.creationTime != null
        origin.executionTime == "10"
        origin.buildInvocationId == buildInvocationId
    }
}
