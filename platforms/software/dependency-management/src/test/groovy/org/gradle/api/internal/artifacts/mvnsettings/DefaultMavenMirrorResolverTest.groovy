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
import org.apache.maven.settings.Server
import org.apache.maven.settings.Settings
import org.codehaus.plexus.util.xml.Xpp3DomBuilder
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.ProviderFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

class DefaultMavenMirrorResolverTest extends Specification {
    // Generated with plexus-cipher 2.0: the master password 'gradle-prototype-master' encrypted
    // with the fixed 'settings.security' key, and 'mirror-secret' encrypted with that master
    static final String ENCRYPTED_MASTER = '{+w6zW/gzt3sHKDw2TQ/+vG1479GJ02Z9URESmQqxB26cQ14p5hMV9v+66BoSriyN}'
    static final String ENCRYPTED_PASSWORD = '{0HkYKhjpO2IH/9BoIL1EsU4QYSX9MtFNu23gH81yTeY=}'

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def settingsProvider = Mock(MavenSettingsProvider)
    def providerFactory = Mock(ProviderFactory)

    def resolver = new DefaultMavenMirrorResolver(settingsProvider, providerFactory)

    def "returns no mirror when feature flag is not set"() {
        given:
        providerFactory.gradleProperty(DefaultMavenMirrorResolver.ENABLE_PROPERTY) >> Providers.notDefined()

        when:
        def result = resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo")

        then:
        !result.present
        0 * settingsProvider.buildSettings()
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
        mirrors(mirror("selective", mirrorOf, "https://mirror.example.com/maven2"))

        expect:
        !resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo").present

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
        resolver.mirrorFor(URI.create("https://repo.example.com/maven2/"), "corp-repo").present
        !resolver.mirrorFor(URI.create("https://repo.example.com/maven2/"), "other-repo").present
    }

    def "first matching mirror wins when several are declared"() {
        given:
        featureEnabled()
        mirrors(
            mirror("selective", "other-repo", "https://selective.example.com/maven2"),
            mirror("first", "*", "https://first.example.com/maven2"),
            mirror("second", "*", "https://second.example.com/maven2"))

        when:
        def result = resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo")

        then:
        result.get().id == "first"
        result.get().url == URI.create("https://first.example.com/maven2")
    }

    def "different repositories can match different mirrors"() {
        given:
        featureEnabled()
        mirrors(
            mirror("central-mirror", "central", "https://central.example.com/maven2"),
            mirror("corp", "corp-repo", "https://corp.example.com/maven2"))

        expect:
        resolver.mirrorFor(URI.create("https://repo.maven.apache.org/maven2/"), "MavenRepo").get().id == "central-mirror"
        resolver.mirrorFor(URI.create("https://repo.example.com/maven2/"), "corp-repo").get().id == "corp"
        !resolver.mirrorFor(URI.create("https://repo.example.com/maven2/"), "unmatched").present
    }

    def "blocked mirror is returned with the blocked flag"() {
        given:
        featureEnabled()
        def blocker = mirror("blocker", "*", "http://0.0.0.0/")
        blocker.blocked = true
        mirrors(blocker)

        when:
        def result = resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo")

        then:
        result.get().blocked
        result.get().credentials == null
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

    def "returns no mirror when settings cannot be read"() {
        given:
        featureEnabled()
        settingsProvider.buildSettings() >> { throw new RuntimeException("broken") }

        expect:
        !resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo").present
    }

    def "parses settings only once"() {
        given:
        featureEnabled()

        when:
        resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo")
        resolver.mirrorFor(URI.create("https://google.example.com/maven2/"), "other-repo")

        then:
        1 * settingsProvider.buildSettings() >> settingsWith(mirror("corp-mirror", "*", "https://mirror.example.com/maven2"))
    }

    def "mirror credentials come from the settings server entry matching the mirror id"() {
        given:
        featureEnabled()
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")],
            [server("other", "nobody", "nothing"), server("corp-mirror", "mirror-user", "plain-secret")])

        when:
        def result = resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo")

        then:
        result.get().credentials.username == "mirror-user"
        result.get().credentials.password == "plain-secret"
    }

    def "mirror has no credentials when no server entry matches the mirror id"() {
        given:
        featureEnabled()
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")], [server("other", "nobody", "nothing")])

        expect:
        resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo").get().credentials == null
    }

    def "gradle properties override the settings server credentials"() {
        given:
        featureEnabled(["corp-mirrorUsername": "prop-user", "corp-mirrorPassword": "prop-secret"])
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")], [server("corp-mirror", "mirror-user", "plain-secret")])

        when:
        def result = resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo")

        then:
        result.get().credentials.username == "prop-user"
        result.get().credentials.password == "prop-secret"
    }

    def "partial gradle properties are ignored and the settings server credentials are used"() {
        given:
        featureEnabled(["corp-mirrorUsername": "prop-user"])
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")], [server("corp-mirror", "mirror-user", "plain-secret")])

        when:
        def result = resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo")

        then:
        result.get().credentials.username == "mirror-user"
        result.get().credentials.password == "plain-secret"
    }

    @RestoreSystemProperties
    def "encrypted server password is decrypted with the maven master password"() {
        given:
        def securityFile = tmpDir.file("settings-security.xml")
        securityFile.text = "<settingsSecurity><master>${ENCRYPTED_MASTER}</master></settingsSecurity>"
        System.setProperty("settings.security", securityFile.absolutePath)

        featureEnabled()
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")], [server("corp-mirror", "mirror-user", ENCRYPTED_PASSWORD)])

        when:
        def result = resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo")

        then:
        result.get().credentials.username == "mirror-user"
        result.get().credentials.password == "mirror-secret"
    }

    @RestoreSystemProperties
    def "mirror is applied without credentials when the password cannot be decrypted"() {
        given:
        System.setProperty("settings.security", tmpDir.file("does-not-exist.xml").absolutePath)

        featureEnabled()
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")], [server("corp-mirror", "mirror-user", ENCRYPTED_PASSWORD)])

        when:
        def result = resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo")

        then:
        result.present
        result.get().credentials == null
    }

    def "http header from the settings server entry is used for the mirror"() {
        given:
        featureEnabled()
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")],
            [serverWithHeaders("corp-mirror", ["Private-Token": "token-123"])])

        when:
        def result = resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo")

        then:
        result.get().credentials == null
        result.get().httpHeader.name == "Private-Token"
        result.get().httpHeader.value == "token-123"
    }

    def "only the first http header is applied"() {
        given:
        featureEnabled()
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")],
            [serverWithHeaders("corp-mirror", ["First-Header": "first", "Second-Header": "second"])])

        when:
        def result = resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo")

        then:
        result.get().httpHeader.name == "First-Header"
        result.get().httpHeader.value == "first"
    }

    def "username and password win over http headers"() {
        given:
        featureEnabled()
        def server = serverWithHeaders("corp-mirror", ["Private-Token": "token-123"])
        server.username = "mirror-user"
        server.password = "plain-secret"
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")], [server])

        when:
        def result = resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo")

        then:
        result.get().credentials.username == "mirror-user"
        result.get().httpHeader == null
    }

    @RestoreSystemProperties
    def "encrypted http header value is decrypted with the maven master password"() {
        given:
        def securityFile = tmpDir.file("settings-security.xml")
        securityFile.text = "<settingsSecurity><master>${ENCRYPTED_MASTER}</master></settingsSecurity>"
        System.setProperty("settings.security", securityFile.absolutePath)

        featureEnabled()
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")],
            [serverWithHeaders("corp-mirror", ["Private-Token": ENCRYPTED_PASSWORD])])

        when:
        def result = resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo")

        then:
        result.get().httpHeader.value == "mirror-secret"
    }

    def "malformed http header configuration is ignored"() {
        given:
        featureEnabled()
        def server = server("corp-mirror", null, null)
        server.configuration = Xpp3DomBuilder.build(new StringReader("""
            <configuration>
                <httpHeaders>
                    <property>
                        <name>Broken-Header</name>
                    </property>
                </httpHeaders>
            </configuration>
        """))
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")], [server])

        when:
        def result = resolver.mirrorFor(URI.create("https://repo1.maven.org/maven2/"), "test-repo")

        then:
        result.present
        result.get().httpHeader == null
        result.get().credentials == null
    }

    private void featureEnabled(Map<String, String> gradleProperties = [:]) {
        gradleProperties.each { name, value ->
            providerFactory.gradleProperty(name) >> Providers.of(value)
        }
        providerFactory.gradleProperty(DefaultMavenMirrorResolver.ENABLE_PROPERTY) >> Providers.of("true")
        providerFactory.gradleProperty(_) >> Providers.notDefined()
        providerFactory.of(MavenSettingsChecksumValueSource, _) >> Providers.notDefined()
    }

    private void mirrors(Mirror... mirrors) {
        settings(mirrors as List)
    }

    private void settings(List<Mirror> mirrors, List<Server> servers = []) {
        def settings = settingsWith(mirrors as Mirror[])
        servers.each { settings.addServer(it) }
        settingsProvider.buildSettings() >> settings
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

    private static Server server(String id, String username, String password) {
        def server = new Server()
        server.id = id
        server.username = username
        server.password = password
        return server
    }

    private static Server serverWithHeaders(String id, Map<String, String> headers) {
        def server = server(id, null, null)
        def properties = headers.collect { name, value ->
            "<property><name>${name}</name><value>${value}</value></property>"
        }.join("\n")
        server.configuration = Xpp3DomBuilder.build(new StringReader("""
            <configuration>
                <httpHeaders>
                    ${properties}
                </httpHeaders>
            </configuration>
        """))
        return server
    }
}
