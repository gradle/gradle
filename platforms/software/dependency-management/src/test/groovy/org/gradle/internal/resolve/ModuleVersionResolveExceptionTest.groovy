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

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector
import static org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.newId
import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class ModuleVersionResolveExceptionTest extends Specification {

    private static ModuleIdentifier mid(String group, String name) {
        DefaultModuleIdentifier.newId(group, name)
    }

    def "provides default message that includes selector"() {
        def exception1 = new ModuleVersionResolveException(newSelector(mid("org", "a"), "1.2"), new RuntimeException())

        expect:
        exception1.message == 'Could not resolve org:a:1.2.'
    }

    def "can add incoming paths to exception"() {
        def a = newId(mid("org", "a"), "1.2")
        def b = newId(mid("org", "b"), "5")
        def c = newId(mid("org", "c"), "1.0")

        def cause = new RuntimeException()
        def exception = new ModuleVersionResolveException(newSelector(mid("a", "b"), "c"), cause)
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
}
