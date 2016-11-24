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

package org.gradle.api.internal.tasks.cache.origin

import org.gradle.api.internal.TaskInternal
import org.gradle.internal.remote.internal.inet.InetAddressFactory
import org.gradle.internal.time.TimeProvider
import org.gradle.util.GradleVersion
import spock.lang.Specification

class OriginMetadataConverterTest extends Specification {
    def task = Mock(TaskInternal)
    def timeProvider = Mock(TimeProvider)
    def inetAddressFactory = Mock(InetAddressFactory)
    def rootDir = Mock(File)
    def converter = new OriginMetadataConverter(timeProvider, inetAddressFactory, rootDir, "user", "os", GradleVersion.version("3.0"))
    def "converts to origin metadata"() {
        timeProvider.currentTime >> 0
        inetAddressFactory.hostname >> "host"
        task.path >> "path"
        rootDir.absolutePath >> "root"

        def originMetadata = converter.convert(task, 10)
        expect:
        originMetadata.path == "path"
        originMetadata.type == task.getClass().canonicalName
        originMetadata.gradleVersion == "3.0"
        originMetadata.creationTime == 0
        originMetadata.executionTime == 10
        originMetadata.rootPath == "root"
        originMetadata.operatingSystem == "os"
        originMetadata.hostName == "host"
        originMetadata.userName == "user"
    }
}
