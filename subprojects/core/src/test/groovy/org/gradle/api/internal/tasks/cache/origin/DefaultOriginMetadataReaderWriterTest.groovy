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

import spock.lang.Specification

class DefaultOriginMetadataReaderWriterTest extends Specification {
    def "metadata roundtrip"() {
        def originMetadata = new DefaultOriginMetadata("path", "type", "3.0", 0, 10, "root", "os", "host", "user")
        def baos = new ByteArrayOutputStream()
        def writer = new DefaultOriginMetadataWriter()
        writer.writeTo(originMetadata, baos)
        def reader = new DefaultOriginMetadataReader()
        def restored = reader.readFrom(new ByteArrayInputStream(baos.toByteArray()))
        expect:
        originMetadata == restored
        restored.path == "path"
        restored.type == "type"
        restored.gradleVersion == "3.0"
        restored.creationTime == 0
        restored.executionTime == 10
        restored.rootPath == "root"
        restored.operatingSystem == "os"
        restored.hostName == "host"
        restored.userName == "user"
    }
}
