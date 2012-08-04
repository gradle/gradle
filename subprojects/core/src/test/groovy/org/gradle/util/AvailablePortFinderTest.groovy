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

package org.gradle.util

import spock.lang.Specification

class AvailablePortFinderTest extends Specification {
    AvailablePortFinder portFinder

    def "can test for and find an available port"() {
        portFinder = AvailablePortFinder.createPrivate()

        expect:
        portFinder.available(portFinder.nextAvailable)
    }

    def "tries to return different ports on successive invocations"() {
        portFinder = AvailablePortFinder.createPrivate()

        expect:
        portFinder.nextAvailable != portFinder.nextAvailable
    }
}
