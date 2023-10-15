/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.plugins.ide.internal.resolver

import spock.lang.Specification

import java.util.function.Supplier

class DefaultGradleApiSourcesResolverTest extends Specification {

    def "when using multiple repo url override sources then the first not null gradle repo url override is used"() {
        String repoUrl
        when:
        repoUrl = DefaultGradleApiSourcesResolver.gradleLibsRepoUrl(
            "d",
            { null } as Supplier<String>,
            { "a" } as Supplier<String>,
            { "b" } as Supplier<String>
        )
        then:
        repoUrl == "a"
    }

    def "when all repo url override sources return null then the default gradle repo url is used"() {
        String repoUrl
        when:
        repoUrl = DefaultGradleApiSourcesResolver.gradleLibsRepoUrl(
            "d",
            { null } as Supplier<String>,
            { null } as Supplier<String>,
            { null } as Supplier<String>
        )
        then:
        repoUrl == "d"
    }

    def "when there are no repo url override sources then the default gradle repo url is used"() {
        String repoUrl
        when:
        repoUrl = DefaultGradleApiSourcesResolver.gradleLibsRepoUrl("d")
        then:
        repoUrl == "d"
    }
}
