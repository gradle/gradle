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

import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector
import static org.gradle.util.TextUtil.toPlatformLineSeparators

class ModuleVersionNotFoundExceptionTest extends Specification {
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
    http://somewhere
    file:/somewhere""")
    }

    def "formats message for selector and locations when no versions attempted"() {
        def exception = new ModuleVersionNotFoundException(newSelector("org", "a", "1.+"), ["http://somewhere", "file:/somewhere"], [], [])

        expect:
        exception.message == toPlatformLineSeparators("""Could not find any matches for org:a:1.+ as no versions of org:a are available.
Searched in the following locations:
    http://somewhere
    file:/somewhere""")
    }

    def "formats message for selector and locations when versions attempted and non rejected"() {
        def exception = new ModuleVersionNotFoundException(newSelector("org", "a", "1.+"), ["http://somewhere", "file:/somewhere"], ["1.1", "1.2"], [])

        expect:
        exception.message == toPlatformLineSeparators("""Could not find any version that matches org:a:1.+.
Versions that do not match:
    1.1
    1.2
Searched in the following locations:
    http://somewhere
    file:/somewhere""")
    }

    def "formats message for selector and locations when versions attempted and some rejected"() {
        def exception = new ModuleVersionNotFoundException(newSelector("org", "a", "1.+"), ["http://somewhere", "file:/somewhere"], ["0.9", "0.10"], ["1.1", "1.2"])

        expect:
        exception.message == toPlatformLineSeparators("""Could not find any version that matches org:a:1.+.
Versions that do not match:
    0.9
    0.10
Versions rejected by component selection rules:
    1.1
    1.2
Searched in the following locations:
    http://somewhere
    file:/somewhere""")
    }

    def "formats message for selector and locations when versions attempted and all rejected"() {
        def exception = new ModuleVersionNotFoundException(newSelector("org", "a", "1.+"), ["http://somewhere", "file:/somewhere"], [], ["1.1", "1.2"])

        expect:
        exception.message == toPlatformLineSeparators("""Could not find any version that matches org:a:1.+.
Versions rejected by component selection rules:
    1.1
    1.2
Searched in the following locations:
    http://somewhere
    file:/somewhere""")
    }

    def "limits list of candidates"() {
        def exception = new ModuleVersionNotFoundException(newSelector("org", "a", "1.+"), ["http://somewhere", "file:/somewhere"], (1..20).collect { it.toString() }, (1..10).collect { it.toString() })

        expect:
        exception.message == toPlatformLineSeparators("""Could not find any version that matches org:a:1.+.
Versions that do not match:
    1
    2
    3
    4
    5
    + 15 more
Versions rejected by component selection rules:
    1
    2
    3
    4
    5
    + 5 more
Searched in the following locations:
    http://somewhere
    file:/somewhere""")
    }

    def "can add incoming paths to exception"() {
        def a = DefaultModuleComponentIdentifier.newId("org", "a", "1.2")
        def b = DefaultModuleComponentIdentifier.newId("org", "b", "5")
        def c = DefaultModuleComponentIdentifier.newId("org", "c", "1.0")

        def exception = new ModuleVersionNotFoundException(newId("a", "b", "c"), ["http://somewhere"])
        def onePath = exception.withIncomingPaths([[a, b, c]])

        expect:
        onePath.message == toPlatformLineSeparators('''Could not find a:b:c.
Searched in the following locations:
    http://somewhere
Required by:
    org:a:1.2 > org:b:5 > org:c:1.0''')
        onePath.stackTrace == exception.stackTrace
    }

}
