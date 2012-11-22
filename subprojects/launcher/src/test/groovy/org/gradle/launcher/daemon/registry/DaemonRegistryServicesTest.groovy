/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.registry

import org.gradle.launcher.daemon.context.DefaultDaemonContext
import org.gradle.messaging.remote.internal.inet.SocketInetAddress
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

class DaemonRegistryServicesTest extends Specification {
    @Rule TemporaryFolder tmp = new TemporaryFolder()

    def registry(baseDir) {
        new DaemonRegistryServices(tmp.createDir(baseDir))
    }

    def "same daemon registry instance is used for same daemon registry file across service instances"() {
        expect:
        registry("a").get(DaemonRegistry).is(registry("a").get(DaemonRegistry))
        !registry("a").get(DaemonRegistry).is(registry("b").get(DaemonRegistry))
    }
    
    @Rule ConcurrentTestUtil concurrent = new ConcurrentTestUtil()
    
    def "the registry can be concurrently written to"() {
        when:
        def registry = registry("someDir").createDaemonRegistry()
        5.times { idx ->
            concurrent.start {
                def context = new DefaultDaemonContext("$idx", new File("$idx"), new File("$idx"), idx, 5000, [])
                registry.store(new SocketInetAddress(new Inet6Address(), 8888 + idx), context, "foo-$idx", true)
            }
        }
        concurrent.finished()

        then:
        registry.all.size() == 5
    }
}