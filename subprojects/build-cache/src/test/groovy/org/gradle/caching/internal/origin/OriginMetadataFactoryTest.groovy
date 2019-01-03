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
import org.gradle.internal.id.UniqueId
import org.gradle.internal.remote.internal.inet.InetAddressFactory
import org.gradle.internal.time.Clock
import org.gradle.util.GradleVersion
import spock.lang.Specification

class OriginMetadataFactoryTest extends Specification {
    def entry = Mock(CacheableEntity)
    def timeProvider = Mock(Clock)
    def inetAddressFactory = Mock(InetAddressFactory)
    def rootDir = Mock(File)
    def buildInvocationId = UniqueId.generate()
    def factory = new OriginMetadataFactory(timeProvider, inetAddressFactory, rootDir, "user", "os", GradleVersion.version("3.0"), buildInvocationId)

    def "converts to origin metadata"() {
        timeProvider.currentTime >> 0
        inetAddressFactory.hostname >> "host"
        entry.identity >> "identity"
        rootDir.absolutePath >> "root"
        def origin = new Properties()
        def writer = factory.createWriter(entry, 10)
        def baos = new ByteArrayOutputStream()
        writer.execute(baos)
        when:
        def reader = factory.createReader(entry)
        // doesn't explode
        reader.execute(new ByteArrayInputStream(baos.toByteArray()))
        and:
        origin.load(new ByteArrayInputStream(baos.toByteArray()))
        then:
        origin.identity == "identity"
        origin.type == entry.getClass().canonicalName
        origin.gradleVersion == "3.0"
        origin.creationTime == "0"
        origin.executionTime == "10"
        origin.rootPath == "root"
        origin.operatingSystem == "os"
        origin.hostName == "host"
        origin.userName == "user"
        origin.buildInvocationId == buildInvocationId.asString()
    }
}
