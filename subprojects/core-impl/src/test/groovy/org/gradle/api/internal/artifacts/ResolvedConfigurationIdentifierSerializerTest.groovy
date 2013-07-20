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

package org.gradle.api.internal.artifacts

import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId

/**
 * By Szczepan Faber on 7/20/13
 */
class ResolvedConfigurationIdentifierSerializerTest extends Specification {

    def s = new ResolvedConfigurationIdentifierSerializer()

    def "serializes"() {
        def bytes = new ByteArrayOutputStream()
        def id = newId("org", "foo", "2.0")
        s.write(bytes, new ResolvedConfigurationIdentifier(id, "conf"))

        when:
        def out = s.read(new ByteArrayInputStream(bytes.toByteArray()))

        then:
        out.configuration == "conf"
        out.id == id
    }
}
