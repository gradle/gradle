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

package org.gradle.launcher.daemon.registry

import org.gradle.internal.nativeplatform.ProcessEnvironment
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.context.DaemonContextBuilder
import org.gradle.messaging.remote.Address
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.cache.internal.DefaultFileLockManagerTestHelper.createDefaultFileLockManager
import static org.gradle.cache.internal.DefaultFileLockManagerTestHelper.unlockUncleanly

class PersistentDaemonRegistryTest extends Specification {

    @Rule TestNameTestDirectoryProvider tmp
    
    int addressCounter = 0
    def lockManager = createDefaultFileLockManager()

    def "corrupt registry file is ignored"() {
        given:
        def file = tmp.file("registry")
        def registry = new PersistentDaemonRegistry(file, lockManager)
        
        and:
        registry.store(address(), daemonContext(), "password", true)

        expect:
        registry.all.size() == 1

        when:
        unlockUncleanly(file)

        then:
        registry.all.empty
    }

    def "safely removes from registry file"() {
        given:
        def registry = new PersistentDaemonRegistry(tmp.file("registry"), lockManager)
        def address = address()

        and:
        registry.store(address, daemonContext(), "password", true)

        when:
        registry.remove(address)

        then:
        registry.all.empty

        and: //it is safe to remove it again
        registry.remove(address)
    }

    def "safely removes if registry empty"() {
        given:
        def registry = new PersistentDaemonRegistry(tmp.file("registry"), lockManager)
        def address = address()

        when:
        registry.remove(address)

        then:
        registry.all.empty
    }
    
    DaemonContext daemonContext() {
        new DaemonContextBuilder([maybeGetPid: {null}] as ProcessEnvironment).with {
            daemonRegistryDir = tmp.createDir("daemons")
            create()
        }
    }
    
    Address address(int i = addressCounter++) {
        new TestAddress(i.toString())
    }

    private static class TestAddress implements Address {

        final String displayName

        TestAddress(String displayName) {
            this.displayName = displayName
        }

        boolean equals(o) {
            displayName == o.displayName
        }

        int hashCode() {
            displayName.hashCode()
        }
    }

}
