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

import org.apache.maven.settings.Mirror
import org.apache.maven.settings.Settings
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.ProviderFactory
import spock.lang.Specification

class DefaultMavenMirrorResolverTest extends Specification {
    def settingsProvider = Mock(MavenSettingsProvider)
    def providerFactory = Mock(ProviderFactory)

    def resolver = new DefaultMavenMirrorResolver(settingsProvider, providerFactory)

    def "returns no mirror when feature flag is not set"() {
        given:
        providerFactory.gradleProperty(DefaultMavenMirrorResolver.ENABLE_PROPERTY) >> Providers.notDefined()

        when:
        def result = resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"))

        then:
        !result.present
        0 * settingsProvider.buildSettings()
    }

    def "returns no mirror when settings declare no mirrors"() {
        given:
        featureEnabled()
        mirrors()

        expect:
        !resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/")).present
    }

    def "returns wildcard mirror for remote repository url"() {
        given:
        featureEnabled()
        mirrors(mirror("corp-mirror", "*", "https://mirror.example.com/maven2"))

        when:
        def result = resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"))

        then:
        result.present
        result.get().id == "corp-mirror"
        result.get().url == URI.create("https://mirror.example.com/maven2")
    }

    def "ignores mirror with unsupported mirrorOf value"() {
        given:
        featureEnabled()
        mirrors(mirror("selective", mirrorOf, "https://mirror.example.com/maven2"))

        expect:
        !resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/")).present

        where:
        mirrorOf << ["external:*", "!central", "central", "repo1,repo2", "*,!central"]
    }

    def "first wildcard mirror wins when several are declared"() {
        given:
        featureEnabled()
        mirrors(
            mirror("first", "*", "https://first.example.com/maven2"),
            mirror("second", "*", "https://second.example.com/maven2"))

        when:
        def result = resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"))

        then:
        result.get().id == "first"
        result.get().url == URI.create("https://first.example.com/maven2")
    }

    def "wildcard mirror after an unsupported mirror is still used"() {
        given:
        featureEnabled()
        mirrors(
            mirror("selective", "external:*", "https://selective.example.com/maven2"),
            mirror("wildcard", "*", "https://wildcard.example.com/maven2"))

        when:
        def result = resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"))

        then:
        result.get().id == "wildcard"
    }

    def "returns no mirror when the url is already the mirror url"() {
        given:
        featureEnabled()
        mirrors(mirror("corp-mirror", "*", "https://mirror.example.com/maven2"))

        expect:
        !resolver.mirrorFor(URI.create("https://mirror.example.com/maven2")).present
    }

    def "does not mirror non-remote urls"() {
        given:
        featureEnabled()
        mirrors(mirror("corp-mirror", "*", "https://mirror.example.com/maven2"))

        expect:
        !resolver.mirrorFor(URI.create("file:/home/user/.m2/repository")).present
    }

    def "returns no mirror when settings cannot be read"() {
        given:
        featureEnabled()
        settingsProvider.buildSettings() >> { throw new RuntimeException("broken") }

        expect:
        !resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/")).present
    }

    def "parses settings only once"() {
        given:
        featureEnabled()

        when:
        resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"))
        resolver.mirrorFor(URI.create("https://google.example.com/maven2/"))

        then:
        1 * settingsProvider.buildSettings() >> settingsWith(mirror("corp-mirror", "*", "https://mirror.example.com/maven2"))
    }

    private void featureEnabled() {
        providerFactory.gradleProperty(DefaultMavenMirrorResolver.ENABLE_PROPERTY) >> Providers.of("true")
        providerFactory.of(MavenSettingsChecksumValueSource, _) >> Providers.notDefined()
    }

    private void mirrors(Mirror... mirrors) {
        settingsProvider.buildSettings() >> settingsWith(mirrors)
    }

    private static Settings settingsWith(Mirror... mirrors) {
        def settings = new Settings()
        mirrors.each { settings.addMirror(it) }
        return settings
    }

    private static Mirror mirror(String id, String mirrorOf, String url) {
        def mirror = new Mirror()
        mirror.id = id
        mirror.mirrorOf = mirrorOf
        mirror.url = url
        return mirror
    }
}
