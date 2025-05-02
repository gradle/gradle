/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.vcs.internal


import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.vcs.VcsMapping
import org.gradle.vcs.git.GitVersionControlSpec
import spock.lang.Specification

class DefaultVersionControlRepositoryTest extends Specification {
    def uri = new URI("https://gradle.org/test")
    def notationParser = Stub(NotationParser)
    def spec = Stub(GitVersionControlSpec)
    def repository = new DefaultVersionControlRepository(uri, notationParser, spec)
    def mapping = Mock(VcsMapping)

    def setup() {
        _ * notationParser.parseNotation(_) >> { String s -> def v = s.split(':'); return DefaultModuleIdentifier.newId(v[0], v[1])}
    }

    def "repository definition does nothing by default"() {
        when:
        repository.asMappingAction().execute(mapping)

        then:
        0 * mapping._
    }

    def "attaches git repo when requested module matches one of those produced by the repo"() {
        def selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("test", "test2"), "1.2")
        def other = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("test", "not-test"), "1.2")

        given:
        repository.producesModule("test:test1")
        repository.producesModule("test:test2")

        when:
        repository.asMappingAction().execute(mapping)

        then:
        _ * mapping.requested >> selector
        1 * mapping.from(spec)
        0 * mapping._

        when:
        repository.asMappingAction().execute(mapping)

        then:
        _ * mapping.requested >> other
        0 * mapping._
    }
}
