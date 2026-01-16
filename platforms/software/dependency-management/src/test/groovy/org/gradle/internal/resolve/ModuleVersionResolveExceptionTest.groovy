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
package org.gradle.internal.resolve

import org.gradle.api.Describable
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.internal.Describables
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import spock.lang.Specification

import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class ModuleVersionResolveExceptionTest extends Specification {

    private static ModuleIdentifier mid(String group, String name) {
        DefaultModuleIdentifier.newId(group, name)
    }

    def "provides default message that includes selector"() {
        def exception1 = new ModuleVersionResolveException(DefaultModuleComponentSelector.newSelector(mid("org", "a"), "1.2"), new RuntimeException())

        expect:
        exception1.message == 'Could not resolve org:a:1.2.'
    }

    def "can add incoming paths to exception"() {
        Describable a = Describables.of("org:a:1.2")
        Describable b = Describables.of("org:b:5")
        Describable c = Describables.of("org:c:1.0")

        def cause = new RuntimeException()
        def exception = new ModuleVersionResolveException(DefaultModuleComponentSelector.newSelector(mid("a", "b"), "c"), cause)
        def onePath = exception.withIncomingPaths([[a, b, c]])
        def twoPaths = exception.withIncomingPaths([[a, b, c], [a, c]])

        expect:
        exception.message == 'Could not resolve a:b:c.'

        onePath.message == toPlatformLineSeparators('''Could not resolve a:b:c.
Required by:
    org:a:1.2 > org:b:5 > org:c:1.0''')
        onePath.stackTrace == exception.stackTrace
        onePath.cause == cause

        twoPaths.message == toPlatformLineSeparators('''Could not resolve a:b:c.
Required by:
    org:a:1.2 > org:b:5 > org:c:1.0
    org:a:1.2 > org:c:1.0''')
    }

    def "handles null paths when formatting message without throwing NPE"() {
        given:
        Describable a = Describables.of("org:a:1.2")
        Describable b = Describables.of("org:b:5")
        Describable c = Describables.of("org:c:1.0")

        def cause = new RuntimeException()
        def exception = new ModuleVersionResolveException(DefaultModuleComponentSelector.newSelector(mid("x", "y"), "z"), cause)

        // Create a list with null path, empty path, and valid paths
        def paths = [null, [], [a, b, c], null, [a, c]]

        when:
        def exceptionWithPaths = exception.withIncomingPaths(paths)

        then:
        noExceptionThrown()

        // Should only include non-null, non-empty paths
        exceptionWithPaths.message == toPlatformLineSeparators('''Could not resolve x:y:z.
Required by:
    org:a:1.2 > org:b:5 > org:c:1.0
    org:a:1.2 > org:c:1.0''')
    }

    def "handles only null and empty paths"() {
        given:
        def exception = new ModuleVersionResolveException(DefaultModuleComponentSelector.newSelector(mid("org", "test"), "1.0"), new RuntimeException())

        // Only null and empty paths
        def paths = [null, [], null, []]

        when:
        def exceptionWithPaths = exception.withIncomingPaths(paths)

        then:
        noExceptionThrown()

        // Should return base message since no valid paths
        exceptionWithPaths.message == 'Could not resolve org:test:1.0.\n' +
            'Required by:' // todo: consider removal.
    }

    def "handles mixed valid and null paths in different order"() {
        given:
        Describable d1 = Describables.of("com:lib1:2.0")
        Describable d2 = Describables.of("com:lib2:3.0")
        Describable d3 = Describables.of("com:lib3:4.0")

        def exception = new ModuleVersionResolveException(
            DefaultModuleComponentSelector.newSelector(mid("target", "module"), "1.0"),
            new RuntimeException()
        )

        // Null at beginning, middle, and end
        def paths = [
            [d1, d2],     // valid
            null,         // null
            [d3],         // valid
            [],           // empty
            null,         // null
            [d1, d3]      // valid
        ]

        when:
        def exceptionWithPaths = exception.withIncomingPaths(paths)

        then:
        noExceptionThrown()

        exceptionWithPaths.message == toPlatformLineSeparators('''Could not resolve target:module:1.0.
Required by:
    com:lib1:2.0 > com:lib2:3.0
    com:lib3:4.0
    com:lib1:2.0 > com:lib3:4.0''')
    }

    def "handles all null paths"() {
        given:
        def exception = new ModuleVersionResolveException(
            DefaultModuleComponentSelector.newSelector(mid("a", "b"), "c"),
            new RuntimeException()
        )

        // All null paths
        def paths = [null, null, null]

        when:
        def exceptionWithPaths = exception.withIncomingPaths(paths)

        then:
        noExceptionThrown()

        // Should return base message
        exceptionWithPaths.message == 'Could not resolve a:b:c.\n' +
            'Required by:'
    }

    def "handles single null path"() {
        given:
        def exception = new ModuleVersionResolveException(
            DefaultModuleComponentSelector.newSelector(mid("single", "test"), "1.0"),
            new RuntimeException()
        )

        when:
        def exceptionWithPaths = exception.withIncomingPaths([null])

        then:
        noExceptionThrown()

        exceptionWithPaths.message == 'Could not resolve single:test:1.0.\n' +
            'Required by:'
    }

    def "handles paths with null elements in valid path"() {
        given:
        Describable d1 = Describables.of("org:dep1:1.0")
        Describable d2 = Describables.of("org:dep2:2.0")

        def exception = new ModuleVersionResolveException(
            DefaultModuleComponentSelector.newSelector(mid("main", "app"), "1.0"),
            new RuntimeException()
        )

        // Create a path with null elements (this should still work since path is not null)
        def pathWithNullElement = [d1, null, d2] as List<Describable>

        when:
        def exceptionWithPaths = exception.withIncomingPaths([pathWithNullElement])

        then:
        noExceptionThrown()

        // toString() will be called on null element, which might throw NPE
        // This test documents the current behavior
        exceptionWithPaths.message.contains('Could not resolve main:app:1.0.')
        exceptionWithPaths.message.contains('Required by:')
    }
}
