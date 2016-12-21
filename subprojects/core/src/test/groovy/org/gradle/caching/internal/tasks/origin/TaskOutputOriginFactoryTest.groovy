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

package org.gradle.caching.internal.tasks.origin

import org.gradle.api.internal.TaskInternal
import org.gradle.internal.remote.internal.inet.InetAddressFactory
import org.gradle.internal.time.TimeProvider
import org.gradle.util.GradleVersion
import spock.lang.Specification

class TaskOutputOriginFactoryTest extends Specification {
    def task = Mock(TaskInternal)
    def timeProvider = Mock(TimeProvider)
    def inetAddressFactory = Mock(InetAddressFactory)
    def rootDir = Mock(File)
    def factory = new TaskOutputOriginFactory(timeProvider, inetAddressFactory, rootDir, "user", "os", GradleVersion.version("3.0"))

    def "converts to origin metadata"() {
        timeProvider.currentTime >> 0
        inetAddressFactory.hostname >> "host"
        task.path >> "path"
        rootDir.absolutePath >> "root"
        def origin = new Properties()
        def writer = factory.createWriter(task, 10)
        def baos = new ByteArrayOutputStream()
        writer.execute(baos)
        when:
        def reader = factory.createReader(task)
        // doesn't explode
        reader.execute(new ByteArrayInputStream(baos.toByteArray()))
        and:
        origin.load(new ByteArrayInputStream(baos.toByteArray()))
        then:
        origin.path == "path"
        origin.type == task.getClass().canonicalName
        origin.gradleVersion == "3.0"
        origin.creationTime == "0"
        origin.executionTime == "10"
        origin.rootPath == "root"
        origin.operatingSystem == "os"
        origin.hostName == "host"
        origin.userName == "user"
    }
}
