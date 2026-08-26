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

import org.gradle.api.internal.StartParameterInternal
import spock.lang.Specification

/**
 * Covers matching a repository against the mirrors declared in the Maven settings. Parsing the
 * settings into those mirrors is {@link DefaultMavenSettingsProviderTest}'s subject.
 */
class DefaultMavenMirrorResolverTest extends Specification {
    def settingsProvider = Mock(MavenSettingsProvider)
    def startParameter = new StartParameterInternal()

    def resolver = new DefaultMavenMirrorResolver(settingsProvider, startParameter)

    def "returns no mirror when feature flag is not set"() {
        given:
        assert !startParameter.sharedMavenMirrorSettings

        when:
        def result = resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo")

        then:
        !result.present
        0 * settingsProvider.getMirrors()
    }

    def "does not read the settings when the feature flag is not set"() {
        when:
        resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo")

        then:
        0 * settingsProvider._
    }

    def "returns no mirror when settings declare no mirrors"() {
        given:
        featureEnabled()
        mirrors()

        expect:
        !resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo").present
    }

    def "returns wildcard mirror for remote repository url"() {
        given:
        featureEnabled()
        mirrors(mirror("corp-mirror", "*", "https://mirror.example.com/maven2"))

        when:
        def result = resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo")

        then:
        result.present
        result.get().id == "corp-mirror"
        result.get().url == URI.create("https://mirror.example.com/maven2")
    }

    def "mirror with non-matching mirrorOf is not applied"() {
        given:
        featureEnabled()
        mirrors(mirror("corp-mirror", mirrorOf, "https://mirror.example.com/maven2"))

        expect:
        !resolver.mirrorFor(URI.create("https://repo.example.com/maven2/"), "test-repo").present

        where:
        mirrorOf << ["central", "other-repo", "repo1,repo2", "*,!test-repo", "external:http:*", ""]
    }

    def "mirrorOf central matches the maven central url regardless of the repository name"() {
        given:
        featureEnabled()
        mirrors(mirror("central-mirror", "central", "https://mirror.example.com/maven2"))

        expect:
        resolver.mirrorFor(URI.create("https://repo.maven.apache.org/maven2/"), "MavenRepo").present
        resolver.mirrorFor(URI.create("https://repo.maven.apache.org/maven2"), "some-name").present
        !resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo").present
    }

    def "central url repository is matched by the central id and not by its gradle name"() {
        given:
        featureEnabled()
        mirrors(mirror("name-mirror", "MavenRepo", "https://mirror.example.com/maven2"))

        expect:
        !resolver.mirrorFor(URI.create("https://repo.maven.apache.org/maven2/"), "MavenRepo").present
    }

    def "mirrorOf matches the gradle repository name"() {
        given:
        featureEnabled()
        mirrors(mirror("corp", "corp-repo", "https://mirror.example.com/maven2"))

        expect:
        resolver.mirrorFor(URI.create("https://repo.example.com/maven2/"), "corp-repo").get().id == "corp"
        !resolver.mirrorFor(URI.create("https://repo.example.com/maven2/"), "unmatched").present
    }

    def "first matching mirror wins when several are declared"() {
        given:
        featureEnabled()
        mirrors(
            mirror("first", "*", "https://first.example.com/maven2"),
            mirror("second", "*", "https://second.example.com/maven2"))

        expect:
        resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo").get().id == "first"
    }

    def "different repositories can match different mirrors"() {
        given:
        featureEnabled()
        mirrors(
            mirror("a-mirror", "repo-a", "https://a.example.com/maven2"),
            mirror("b-mirror", "repo-b", "https://b.example.com/maven2"))

        expect:
        resolver.mirrorFor(URI.create("https://repo.example.com/maven2/"), "repo-a").get().id == "a-mirror"
        resolver.mirrorFor(URI.create("https://repo.example.com/maven2/"), "repo-b").get().id == "b-mirror"
    }

    def "blocked mirror is returned rather than filtered out"() {
        given:
        featureEnabled()
        mirrors(mirror("blocker", "*", "http://0.0.0.0/", true))

        expect:
        resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo").get().blocked
    }

    def "returns no mirror when the url is already the mirror url"() {
        given:
        featureEnabled()
        mirrors(mirror("corp-mirror", "*", "https://mirror.example.com/maven2"))

        expect:
        !resolver.mirrorFor(URI.create("https://mirror.example.com/maven2"), "test-repo").present
    }

    def "does not mirror non-remote urls"() {
        given:
        featureEnabled()
        mirrors(mirror("corp-mirror", "*", "https://mirror.example.com/maven2"))

        expect:
        !resolver.mirrorFor(URI.create("file:/home/user/.m2/repository"), "mavenLocal").present
    }

    private void featureEnabled() {
        startParameter.sharedMavenMirrorSettings = true
    }

    private void mirrors(MavenSettingsProvider.MirroredRepository... mirrors) {
        settingsProvider.getMirrors() >> (mirrors as List)
    }

    private static MavenSettingsProvider.MirroredRepository mirror(String id, String mirrorOf, String url, boolean blocked = false) {
        return new MavenSettingsProvider.MirroredRepository(mirrorOf, id, URI.create(url), blocked, null, null)
    }
}
