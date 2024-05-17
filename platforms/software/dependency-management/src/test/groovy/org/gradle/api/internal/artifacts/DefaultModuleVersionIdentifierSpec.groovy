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
package org.gradle.api.internal.artifacts

import spock.lang.Specification

import static org.gradle.util.Matchers.strictlyEqual

class DefaultModuleVersionIdentifierSpec extends Specification {
    def "has useful toString()"() {
        def module = new DefaultModuleVersionIdentifier("group", "module", "version")

        expect:
        module.toString().contains("group:module:version")
    }

    def "ids are equal when group, module and version are equal"() {
        def module = new DefaultModuleVersionIdentifier("group", "module", "version")
        def same = new DefaultModuleVersionIdentifier("group", "module", "version")
        def differentGroup = new DefaultModuleVersionIdentifier("other", "module", "version")
        def differentModule = new DefaultModuleVersionIdentifier("group", "other", "version")
        def differentVersion = new DefaultModuleVersionIdentifier("group", "module", "other")

        expect:
        module strictlyEqual(same)
        module != differentGroup
        module != differentModule
        module != differentVersion
    }

    def "provides module identifier"() {
        expect:
        def id = new DefaultModuleVersionIdentifier("org.gradle", "tooling-api", "1.3")
        id.group == id.module.group
        id.name == id.module.name
    }
}
