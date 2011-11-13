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
package org.gradle.api.internal.artifacts.ivyservice

import spock.lang.Specification
import org.apache.ivy.core.module.id.ModuleRevisionId
import static org.gradle.util.TextUtil.*

class ModuleVersionResolveExceptionTest extends Specification {
    def "can add incoming paths to exception"() {
        def a = ModuleRevisionId.newInstance("org", "a", "1.2")
        def b = ModuleRevisionId.newInstance("org", "b", "5")
        def c = ModuleRevisionId.newInstance("org", "c", "1.0")

        def exception = new ModuleVersionResolveException("broken")
        def onePath = exception.withIncomingPaths([[a, b, c]])
        def twoPaths = exception.withIncomingPaths([[a, b, c], [a, c]])

        expect:
        exception.message == 'broken'

        onePath.message == toPlatformLineSeparators('''broken
Required by:
    org:a:1.2 > org:b:5 > org:c:1.0''')
        onePath.stackTrace == exception.stackTrace

        twoPaths.message == toPlatformLineSeparators('''broken
Required by:
    org:a:1.2 > org:b:5 > org:c:1.0
    org:a:1.2 > org:c:1.0''')
    }
}
