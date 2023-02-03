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

import org.gradle.api.Action
import org.gradle.api.internal.file.TestFiles
import org.gradle.vcs.VcsMappings
import spock.lang.Specification

class DefaultSourceControlTest extends Specification {
    def sourceControl = new DefaultSourceControl(TestFiles.resolver(), Stub(VcsMappings), Stub(VersionControlSpecFactory))

    def "can register a Git repository"() {
        expect:
        def repo = sourceControl.gitRepository(new URI("https://gradle.org/test1"))
        repo != null
        def repo2 = sourceControl.gitRepository(new URI("https://gradle.org/test2"))
        repo2 != repo
    }

    def "can register and configure a Git repository"() {
        def action = Mock(Action)

        when:
        def repo = sourceControl.gitRepository(new URI("https://gradle.org/test1"), action)
        repo != null

        then:
        1 * action.execute({it != null})
    }

    def "can register a Git repository multiple times"() {
        def url = new URI("https://gradle.org/test1")

        expect:
        def repo = sourceControl.gitRepository(url)
        sourceControl.gitRepository(url).is(repo)
        sourceControl.gitRepository(url) {
            assert it.is(repo)
        }
    }
}
