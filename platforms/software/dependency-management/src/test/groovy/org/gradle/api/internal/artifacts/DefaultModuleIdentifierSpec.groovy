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

import static org.gradle.util.Matchers.strictlyEqual

class DefaultModuleIdentifierSpec extends Specification {

    def "has useful toString()"() {
        def module = DefaultModuleIdentifier.newId("org.foo", "bar")

        expect:
        module.toString().contains("org.foo:bar")
    }

    def "ids are equal when group, module and version are equal"() {
        def module = DefaultModuleIdentifier.newId("group", "module")
        def same = DefaultModuleIdentifier.newId("group", "module")
        def differentGroup = DefaultModuleIdentifier.newId("other", "module")
        def differentModule = DefaultModuleIdentifier.newId("group", "other")

        expect:
        module strictlyEqual(same)
        module != differentGroup
        module != differentModule
    }
}
