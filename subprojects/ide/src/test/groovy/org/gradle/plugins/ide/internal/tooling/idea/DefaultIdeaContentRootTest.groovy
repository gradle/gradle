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

package org.gradle.plugins.ide.internal.tooling.idea

import spock.lang.Specification

class DefaultIdeaContentRootTest extends Specification {
    def contentRoot = new DefaultIdeaContentRoot()

    def "generated source is a subset of source"() {
        def dir = Stub(DefaultIdeaSourceDirectory)
        def generated = Stub(DefaultIdeaSourceDirectory)

        given:
        generated.generated >> true
        contentRoot.sourceDirectories = [dir, generated]

        expect:
        contentRoot.generatedSourceDirectories == [generated] as Set
    }

    def "generated test source is a subset of test source"() {
        def dir = Stub(DefaultIdeaSourceDirectory)
        def generated = Stub(DefaultIdeaSourceDirectory)

        given:
        generated.generated >> true
        contentRoot.testDirectories = [dir, generated]

        expect:
        contentRoot.generatedTestDirectories == [generated] as Set
    }
}
