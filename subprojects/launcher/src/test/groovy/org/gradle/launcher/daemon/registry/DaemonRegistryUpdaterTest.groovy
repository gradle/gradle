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

import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.registry.DaemonRegistry.EmptyRegistryException
import org.gradle.launcher.daemon.server.DaemonRegistryUpdater
import org.gradle.internal.remote.Address
import spock.lang.Specification

import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.*

public class DaemonRegistryUpdaterTest extends Specification {

    final DaemonRegistry registry = Mock()
    final Address address = Mock()
    final DaemonContext context = Mock()
    final updater = new DaemonRegistryUpdater(registry, context)

    def "marks idle"() {
        given:
        updater.onStart(address)

        when:
        updater.onCompleteActivity()

        then:
        1 * registry.markState(address, Idle)
    }

    def "ignores empty cache on marking idle"() {
        given:
        updater.onStart(address)
        registry.markState(address, Idle) >> { throw new EmptyRegistryException("") }

        when:
        updater.onCompleteActivity()

        then:
        noExceptionThrown()
    }

    def "marks busy"() {
        given:
        updater.onStart(address)

        when:
        updater.onStartActivity()

        then:
        1 * registry.markState(address, Busy)
    }

    def "marks canceled"() {
        given:
        updater.onStart(address)

        when:
        updater.onCancel()

        then:
        1 * registry.markState(address, Canceled)
    }

    def "ignores empty cache on marking busy"() {
        given:
        updater.onStart(address)
        registry.markState(address, Busy) >> { throw new EmptyRegistryException("") }

        when:
        updater.onStartActivity()

        then:
        noExceptionThrown()
    }

    def "ignores empty cache on stopping"() {
        given:
        updater.onStart(address)
        registry.remove(address) >> { throw new EmptyRegistryException("") }

        when:
        updater.stop()

        then:
        noExceptionThrown()
    }
}
