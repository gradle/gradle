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

import org.gradle.launcher.daemon.registry.DaemonRegistry.EmptyRegistryException
import org.gradle.messaging.remote.Address
import org.gradle.launcher.daemon.server.DomainRegistryUpdater
import org.gradle.launcher.daemon.context.DaemonContextBuilder

import spock.lang.Specification

/**
 * @author: Szczepan Faber, created at: 9/12/11
 */
public class DomainRegistryUpdaterTest extends Specification {

    def registry = Mock(DaemonRegistry)
    def address = {} as Address
    def updater = new DomainRegistryUpdater(registry, new DaemonContextBuilder().create(), "password", address)

    def "marks idle"() {
        when:
        updater.onCompleteActivity()

        then:
        1 * registry.markIdle(address)
    }

    def "ignores empty cache on marking idle"() {
        given:
        1 * registry.markIdle(address) >> { throw new EmptyRegistryException("") }

        when:
        updater.onCompleteActivity()

        then:
        noExceptionThrown()
    }

    def "marks busy"() {
        when:
        updater.onStartActivity()

        then:
        1 * registry.markBusy(address)
    }

    def "ignores empty cache on marking busy"() {
        given:
        1 * registry.markBusy(address) >> { throw new EmptyRegistryException("") }

        when:
        updater.onStartActivity()

        then:
        noExceptionThrown()
    }

     def "ignores empty cache on stopping"() {
        given:
        1 * registry.remove(address) >> { throw new EmptyRegistryException("") }

        when:
        updater.onStop()

        then:
        noExceptionThrown()
    }
}
