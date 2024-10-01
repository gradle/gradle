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
package org.gradle.internal.resolve

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.api.internal.attributes.matching.AttributeMatcher
import org.gradle.util.AttributeTestUtil
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.internal.component.external.model.DefaultModuleComponentSelector.newSelector
import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class ModuleVersionNotFoundExceptionTest extends Specification {
    static ModuleIdentifier mid(String group, String name) {
        DefaultModuleIdentifier.newId(group, name)
    }

    def "formats message to include id when no locations"() {
        def exception = new ModuleVersionNotFoundException(newId("org", "a", "1.2"), [])

        expect:
        exception.message == 'Could not find org:a:1.2.'
    }

    def "formats message to include id and locations"() {
        def exception = new ModuleVersionNotFoundException(newId("org", "a", "1.2"), ["http://somewhere", "file:/somewhere"])

        expect:
        exception.message == toPlatformLineSeparators("""Could not find org:a:1.2.
Searched in the following locations:
  - http://somewhere
  - file:/somewhere""")
    }

    def "formats message for selector and locations when no versions attempted"() {
        def exception = new ModuleVersionNotFoundException(newSelector(mid("org", "a"), new DefaultMutableVersionConstraint("1.+")), ["http://somewhere", "file:/somewhere"], [], [])

        expect:
        exception.message == toPlatformLineSeparators("""Could not find any matches for org:a:1.+ as no versions of org:a are available.
Searched in the following locations:
  - http://somewhere
  - file:/somewhere""")
    }

    def "formats message for selector and locations when versions attempted and non rejected"() {
        def exception = new ModuleVersionNotFoundException(newSelector(mid("org", "a"), new DefaultMutableVersionConstraint("1.+")), ["http://somewhere", "file:/somewhere"], ["1.1", "1.2"], [])

        expect:
        exception.message == toPlatformLineSeparators("""Could not find any version that matches org:a:1.+.
Versions that do not match:
  - 1.1
  - 1.2
Searched in the following locations:
  - http://somewhere
  - file:/somewhere""")
    }

    def "formats message for selector and locations when versions attempted and some rejected"() {
        def exception = new ModuleVersionNotFoundException(newSelector(mid("org", "a"), new DefaultMutableVersionConstraint("1.+")), ["http://somewhere", "file:/somewhere"], ["0.9", "0.10"], [reject("1.1"), reject("1.2")])

        expect:
        exception.message == toPlatformLineSeparators("""Could not find any version that matches org:a:1.+.
Versions that do not match:
  - 0.9
  - 0.10
Versions rejected by component selection rules:
  - 1.1
  - 1.2
Searched in the following locations:
  - http://somewhere
  - file:/somewhere""")
    }

    def "formats message for selector and locations when versions attempted and all rejected"() {
        def exception = new ModuleVersionNotFoundException(newSelector(mid("org", "a"), new DefaultMutableVersionConstraint("1.+")), ["http://somewhere", "file:/somewhere"], [], [reject("1.1"), reject("1.2")])

        expect:
        exception.message == toPlatformLineSeparators("""Could not find any version that matches org:a:1.+.
Versions rejected by component selection rules:
  - 1.1
  - 1.2
Searched in the following locations:
  - http://somewhere
  - file:/somewhere""")
    }

    def "limits list of candidates"() {
        def exception = new ModuleVersionNotFoundException(newSelector(mid("org", "a"), new DefaultMutableVersionConstraint("1.+")), ["http://somewhere", "file:/somewhere"], (1..20).collect { it.toString() }, (1..10).collect { reject(it.toString()) })

        expect:
        exception.message == toPlatformLineSeparators("""Could not find any version that matches org:a:1.+.
Versions that do not match:
  - 1
  - 2
  - 3
  - 4
  - 5
  - + 15 more
Versions rejected by component selection rules:
  - 1
  - 2
  - 3
  - 4
  - 5
  - + 5 more
Searched in the following locations:
  - http://somewhere
  - file:/somewhere""")
    }

    def "can add incoming paths to exception"() {
        def a = DefaultModuleComponentIdentifier.newId(mid("org", "a"), "1.2")
        def b = DefaultModuleComponentIdentifier.newId(mid("org", "b"), "5")
        def c = DefaultModuleComponentIdentifier.newId(mid("org", "c"), "1.0")

        def exception = new ModuleVersionNotFoundException(newId("a", "b", "c"), ["http://somewhere"])
        def onePath = exception.withIncomingPaths([[a, b, c]])

        expect:
        onePath.message == toPlatformLineSeparators('''Could not find a:b:c.
Searched in the following locations: http://somewhere
Required by:
    org:a:1.2 > org:b:5 > org:c:1.0''')
        onePath.stackTrace == exception.stackTrace
    }

    def "formats message for selector and locations when versions are rejected by attribute matching"() {
        def versions = [
                rejectedByAttributes('1.1', [color: ['red', 'blue', false]]),
                rejectedByAttributes('1.0', [color: ['red', 'green', false]]),
        ]
        def exception = new ModuleVersionNotFoundException(newSelector(mid("org", "a"), new DefaultMutableVersionConstraint("1.+")), ["http://somewhere", "file:/somewhere"], ["0.9", "0.10"], versions)

        expect:
        exception.message == toPlatformLineSeparators("""Could not find any version that matches org:a:1.+.
Versions that do not match:
  - 0.9
  - 0.10
Versions rejected by attribute matching:
  - 1.1:
      - Attribute 'color' didn't match. Requested 'red', was: 'blue'
  - 1.0:
      - Attribute 'color' didn't match. Requested 'red', was: 'green'
Searched in the following locations:
  - http://somewhere
  - file:/somewhere""")
    }

    def "formats message for selector and locations when versions are rejected by attribute matching with multiple attributes"() {
        def versions = [
                rejectedByAttributes('1.1', [color: ['red', 'red', true], shape: ['square', 'circle', false]]),
                rejectedByAttributes('1.0', [color: ['red', 'green', false], shape: ['square', 'circle', false]]),
        ]
        def exception = new ModuleVersionNotFoundException(newSelector(mid("org", "a"), new DefaultMutableVersionConstraint("1.+")), ["http://somewhere", "file:/somewhere"], ["0.9", "0.10"], versions)

        expect:
        exception.message == toPlatformLineSeparators("""Could not find any version that matches org:a:1.+.
Versions that do not match:
  - 0.9
  - 0.10
Versions rejected by attribute matching:
  - 1.1:
      - Attribute 'color' matched. Requested 'red', was: 'red'
      - Attribute 'shape' didn't match. Requested 'square', was: 'circle'
  - 1.0:
      - Attribute 'color' didn't match. Requested 'red', was: 'green'
      - Attribute 'shape' didn't match. Requested 'square', was: 'circle'
Searched in the following locations:
  - http://somewhere
  - file:/somewhere""")
    }

    def "formats message for selector and locations when versions are rejected by attribute matching and rules"() {
        def versions = [
                reject('1.2'),
                rejectedByAttributes('1.1', [color: ['red', 'red', true], shape: ['square', 'circle', false]]),
                rejectedByAttributes('1.0', [color: ['red', 'green', false], shape: ['square', 'circle', false]]),
        ]
        def exception = new ModuleVersionNotFoundException(newSelector(mid("org", "a"), new DefaultMutableVersionConstraint("1.+")), ["http://somewhere", "file:/somewhere"], ["0.9", "0.10"], versions)

        expect:
        exception.message == toPlatformLineSeparators("""Could not find any version that matches org:a:1.+.
Versions that do not match:
  - 0.9
  - 0.10
Versions rejected by component selection rules: 1.2
Versions rejected by attribute matching:
  - 1.1:
      - Attribute 'color' matched. Requested 'red', was: 'red'
      - Attribute 'shape' didn't match. Requested 'square', was: 'circle'
  - 1.0:
      - Attribute 'color' didn't match. Requested 'red', was: 'green'
      - Attribute 'shape' didn't match. Requested 'square', was: 'circle'
Searched in the following locations:
  - http://somewhere
  - file:/somewhere""")
    }


    static RejectedVersion reject(String version) {
        new RejectedVersion(DefaultModuleComponentIdentifier.newId(mid("org", "foo"), version)) {}
    }

    static RejectedByAttributesVersion rejectedByAttributes(String version, Map<String, List<String>> attributes) {
        return new RejectedByAttributesVersion(DefaultModuleComponentIdentifier.newId(mid("org", "foo"), version), toDescriptions(attributes))
    }

    static List<AttributeMatcher.MatchingDescription> toDescriptions(Map<String, List<String>> attributes) {
        attributes.collect { k, v ->
            def attribute = Attribute.of(k, String)
            new AttributeMatcher.MatchingDescription<Object>(attribute, AttributeTestUtil.attributesFactory().of(attribute, v[0]), AttributeTestUtil.attributesFactory().of(attribute, v[1]), v[2])
        }
    }
}
