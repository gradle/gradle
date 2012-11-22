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

package org.gradle.api.publish.internal

import org.gradle.api.internal.ThreadGlobalInstantiator
import org.gradle.api.publish.Publication
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.UnknownPublicationException
import org.gradle.internal.reflect.Instantiator
import spock.lang.Specification

class DefaultPublicationContainerTest extends Specification {

    Instantiator instantiator = ThreadGlobalInstantiator.getOrCreate()
    PublicationContainer container = instantiator.newInstance(DefaultPublicationContainer, instantiator)

    def "right exception is thrown on unknown access"() {
        given:
        container.add(publication("foo"))

        expect:
        container.foo instanceof Publication

        when:
        container.getByName("notHere")

        then:
        def e = thrown(UnknownPublicationException)
        e.message == "Publication with name 'notHere' not found"
    }

    Publication publication(String name) {
        new Publication() {
            String getName() {
                name
            }
        }
    }
}
