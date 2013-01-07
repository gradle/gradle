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
package org.gradle.api.internal.artifacts.ivyservice

import spock.lang.Specification

import static org.apache.ivy.core.module.id.ModuleRevisionId.newInstance
import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.util.TextUtil.toPlatformLineSeparators

class ModuleVersionNotFoundExceptionTest extends Specification {
    def "formats message to include selector"() {
        def exception1 = new ModuleVersionNotFoundException(newInstance("org", "a", "1.2"))
        def exception2 = new ModuleVersionNotFoundException(newId("org", "a", "1.2"))
        def exception3 = new ModuleVersionNotFoundException(newInstance("org", "a", "1.2"), "nothing matches %s")

        expect:
        exception1.message == 'Could not find org:a:1.2.'
        exception2.message == 'Could not find org:a:1.2.'
        exception3.message == 'nothing matches org:a:1.2'
    }

    def "can add incoming paths to exception"() {
        def a = newInstance("org", "a", "1.2")
        def b = newInstance("org", "b", "5")
        def c = newInstance("org", "c", "1.0")

        def exception = new ModuleVersionNotFoundException(newInstance("a", "b", "c"))
        def onePath = exception.withIncomingPaths([[a, b, c]])

        expect:
        onePath.message == toPlatformLineSeparators('''Could not find a:b:c.
Required by:
    org:a:1.2 > org:b:5 > org:c:1.0''')
        onePath.stackTrace == exception.stackTrace
    }

}
