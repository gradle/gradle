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
package org.gradle.messaging.remote.internal

import spock.lang.Specification
import org.gradle.messaging.remote.Address
import org.gradle.util.Matchers

class CompositeAddressTest extends Specification {
    def "has useful display name"() {
        def target = { "<target>" } as Address
        def qualifier = "<qualifier>"
        def address = new CompositeAddress(target, qualifier)

        expect:
        address.displayName == "<target>:<qualifier>"
        address.toString() == "<target>:<qualifier>"
    }

    def "equal when address and qualifier are equal"() {
        def target = { "<target>" } as Address
        def target2 = { "<target2>" } as Address
        def address = new CompositeAddress(target, "qualifier")
        def same = new CompositeAddress(target, "qualifier")
        def differentAddress = new CompositeAddress(target2, "qualifier")
        def differentQualifier = new CompositeAddress(target, "other")

        expect:
        address Matchers.strictlyEqual(same)
        address != differentAddress
        address != differentQualifier
    }
}
