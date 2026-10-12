/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.api.internal.artifacts.mvnsettings

import spock.lang.Specification

/**
 * The expectations mirror the semantics of Maven's own matcher,
 * org.eclipse.aether.util.repository.DefaultMirrorSelector.
 */
class MirrorOfMatcherTest extends Specification {

    def "pattern '#pattern' against id '#id' at #url -> #expected"() {
        expect:
        MirrorOfMatcher.matches(pattern, id, URI.create(url)) == expected

        where:
        pattern             | id        | url                            | expected
        "*"                 | "any"     | "https://repo.example.com/m2"  | true
        "central"           | "central" | "https://repo.example.com/m2"  | true
        "central"           | "other"   | "https://repo.example.com/m2"  | false
        "repo1,repo2"       | "repo2"   | "https://repo.example.com/m2"  | true
        "repo1, repo2"      | "repo2"   | "https://repo.example.com/m2"  | true
        "repo1,repo2"       | "repo3"   | "https://repo.example.com/m2"  | false
        "*,!excluded"       | "excluded" | "https://repo.example.com/m2" | false
        "*,!excluded"       | "other"   | "https://repo.example.com/m2"  | true
        "!excluded,*"       | "excluded" | "https://repo.example.com/m2" | false
        "external:*"        | "any"     | "https://repo.example.com/m2"  | true
        "external:*"        | "any"     | "http://localhost:8080/m2"     | false
        "external:*"        | "any"     | "http://127.0.0.1:8080/m2"     | false
        "external:*"        | "any"     | "file:/var/repo"               | false
        "external:*,!corp"  | "corp"    | "https://repo.example.com/m2"  | false
        "external:http:*"   | "any"     | "http://repo.example.com/m2"   | true
        "external:http:*"   | "any"     | "https://repo.example.com/m2"  | false
        "external:http:*"   | "any"     | "http://127.0.0.1:8080/m2"     | false
        ""                  | "any"     | "https://repo.example.com/m2"  | false
        null                | "any"     | "https://repo.example.com/m2"  | false
        "!central"          | "central" | "https://repo.example.com/m2"  | false
        "!central"          | "other"   | "https://repo.example.com/m2"  | false
    }
}
