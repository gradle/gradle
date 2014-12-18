/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.initialization

import org.gradle.internal.id.LongIdGenerator
import spock.lang.Specification

class ScopeNodeIdentifierTest extends Specification {

    def "knows loader ids"() {
        def id = new ScopeNodeIdentifier("x", new LongIdGenerator())
        expect:
        id.localId().toString() == "scope:x-local"
        id.exportId().toString() == "scope:x-export"
    }

    def "creates child"() {
        def id = new ScopeNodeIdentifier("x", new LongIdGenerator())
        expect:
        id.newChild().localId().toString() == "scope:x:c1-local"
        id.newChild().exportId().toString() == "scope:x:c2-export"
    }
}
