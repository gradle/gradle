/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.groovy.scripts

import org.gradle.internal.resource.TextResource
import spock.lang.Specification


class CachingScriptSourceTest extends Specification {
    def "creates wrapper for script source when content is expensive to query"() {
        def scriptSource = Stub(ScriptSource)
        def resource = Stub(TextResource)

        scriptSource.resource >> resource
        resource.contentCached >> false

        expect:
        def wrapper = CachingScriptSource.of(scriptSource)
        wrapper instanceof CachingScriptSource
        wrapper.source == scriptSource
    }

    def "does not create wrapper for script source when content is cheap to query"() {
        def scriptSource = Stub(ScriptSource)
        def resource = Stub(TextResource)

        scriptSource.resource >> resource
        resource.contentCached >> true

        expect:
        CachingScriptSource.of(scriptSource) == scriptSource
    }
}
