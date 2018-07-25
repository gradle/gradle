/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.artifacts.ModuleIdentifier
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class DefaultModuleVersionSelectorTest extends Specification {

    private final static ModuleIdentifier UTIL = DefaultModuleIdentifier.newId("org", "util")


    def "equality"() {
        def selector = newSelector(UTIL, "1.0")

        def same = newSelector(UTIL, "1.0")
        def diffGroup = newSelector(DefaultModuleIdentifier.newId("foo", "util"), "1.0")
        def diffName = newSelector(DefaultModuleIdentifier.newId("org", "foo"), "1.0")
        def diffVersion = newSelector(UTIL, "2.0")

        expect:
        selector == same
        selector != diffGroup
        selector != diffName
        selector != diffVersion
    }

    def "knows if matches the id"() {
        def selector = newSelector(UTIL, "1.0")
        def matching = newId(UTIL, "1.0")

        def differentGroup = newId("xorg", "util", "1.0")
        def differentName = newId("org", "xutil", "1.0")
        def differentVersion = newId("org", "xutil", "x1.0")

        expect:
        selector.matchesStrictly(matching)

        !selector.matchesStrictly(differentGroup)
        !selector.matchesStrictly(differentName)
        !selector.matchesStrictly(differentVersion)
    }
}
