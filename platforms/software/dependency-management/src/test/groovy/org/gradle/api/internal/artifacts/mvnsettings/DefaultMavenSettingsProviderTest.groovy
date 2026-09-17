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
import org.apache.maven.settings.io.DefaultSettingsWriter
import org.codehaus.plexus.util.xml.Xpp3DomBuilder
import org.gradle.internal.resource.local.FileResourceListener
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

/**
 * Covers the parsing side of the Maven settings: turning the {@code <mirror>} and
 * {@code <server>} entries into mirror model objects, and declaring the files as build inputs.
 * Matching a repository against a mirror is {@link DefaultMavenMirrorResolverTest}'s subject.
 */
class DefaultMavenSettingsProviderTest extends Specification {
    // Generated with plexus-cipher 2.0: the master password 'gradle-prototype-master' encrypted
    // with the fixed 'settings.security' key, and 'mirror-secret' encrypted with that master
    static final String ENCRYPTED_MASTER = '{+w6zW/gzt3sHKDw2TQ/+vG1479GJ02Z9URESmQqxB26cQ14p5hMV9v+66BoSriyN}'
    static final String ENCRYPTED_PASSWORD = '{0HkYKhjpO2IH/9BoIL1EsU4QYSX9MtFNu23gH81yTeY=}'

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def userSettingsFile = tmpDir.file("settings.xml")
    def globalSettingsFile = tmpDir.file("global-settings.xml")
    def securitySettingsFile = tmpDir.file("settings-security.xml")

    def fileLocations = Stub(MavenFileLocations) {
        getUserSettingsFile() >> userSettingsFile
        getGlobalSettingsFile() >> globalSettingsFile
        getUserSecuritySettingsFile() >> securitySettingsFile
    }
    def fileResourceListener = Mock(FileResourceListener)

    def provider = new DefaultMavenSettingsProvider(fileLocations, fileResourceListener)

    def "declares the settings files as build inputs"() {
        given:
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")])

        when:
        provider.getMirrors()

        then:
        1 * fileResourceListener.fileObserved(userSettingsFile)
        1 * fileResourceListener.fileObserved(globalSettingsFile)
        1 * fileResourceListener.fileObserved(securitySettingsFile)
    }

    def "does not declare the settings security file when only building the settings"() {
        given: "the settings builder leaves {...} passwords encrypted, so it never reads the master password"
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")])

        when:
        provider.buildSettings()

        then:
        1 * fileResourceListener.fileObserved(userSettingsFile)
        1 * fileResourceListener.fileObserved(globalSettingsFile)
        0 * fileResourceListener.fileObserved(securitySettingsFile)
    }

    def "parses the settings only once"() {
        given:
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")])

        when:
        provider.getMirrors()
        provider.getMirrors()

        then: "a second parse would declare the inputs a second time"
        1 * fileResourceListener.fileObserved(userSettingsFile)
    }

    def "returns no mirrors when the settings cannot be read"() {
        given:
        userSettingsFile.text = "<settings><mirrors>not xml at all"

        expect:
        provider.getMirrors().empty
    }

    def "carries the mirrorOf pattern, id and url of each mirror"() {
        given:
        settings([mirror("corp-mirror", "external:*", "https://mirror.example.com/maven2")])

        expect:
        with(onlyMirror()) {
            mirrorOf == "external:*"
            id == "corp-mirror"
            url == URI.create("https://mirror.example.com/maven2")
            !blocked
        }
    }

    def "blocked mirror is parsed with the blocked flag and no credentials"() {
        given:
        def blocker = mirror("blocker", "*", "http://0.0.0.0/")
        blocker.blocked = true
        settings([blocker], [server("blocker", "nobody", "nothing")])

        expect:
        with(onlyMirror()) {
            blocked
            credentials == null
            httpHeader == null
        }
    }

    def "mirror with an invalid url is ignored"() {
        given:
        settings([mirror("corp-mirror", "*", "not a uri")])

        expect:
        provider.getMirrors().empty
    }

    def "credentials come from the server entry matching the mirror id"() {
        given:
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")],
            [server("other", "nobody", "nothing"), server("corp-mirror", "mirror-user", "plain-secret")])

        expect:
        with(onlyMirror().credentials) {
            username == "mirror-user"
            password == "plain-secret"
        }
    }

    def "mirror has no credentials when no server entry matches the mirror id"() {
        given:
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")], [server("other", "nobody", "nothing")])

        expect:
        onlyMirror().credentials == null
    }

    def "encrypted server password is decrypted with the maven master password"() {
        given:
        securitySettingsFile.text = "<settingsSecurity><master>${ENCRYPTED_MASTER}</master></settingsSecurity>"
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")],
            [server("corp-mirror", "mirror-user", ENCRYPTED_PASSWORD)])

        expect:
        with(onlyMirror().credentials) {
            username == "mirror-user"
            password == "mirror-secret"
        }
    }

    def "mirror is parsed without credentials when the password cannot be decrypted"() {
        given: "no settings-security.xml exists, so the master password is unavailable"
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")],
            [server("corp-mirror", "mirror-user", ENCRYPTED_PASSWORD)])

        expect:
        with(onlyMirror()) {
            credentials == null
            httpHeader == null
        }
    }

    def "http header from the server entry is used for the mirror"() {
        given:
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")],
            [serverWithHeaders("corp-mirror", ["Private-Token": "token-123"])])

        expect:
        with(onlyMirror().httpHeader) {
            name == "Private-Token"
            value == "token-123"
        }
    }

    def "only the first http header is applied"() {
        given:
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")],
            [serverWithHeaders("corp-mirror", ["First-Header": "first", "Second-Header": "second"])])

        expect:
        with(onlyMirror().httpHeader) {
            name == "First-Header"
            value == "first"
        }
    }

    def "username and password win over http headers"() {
        given:
        def server = serverWithHeaders("corp-mirror", ["Private-Token": "token-123"])
        server.username = "mirror-user"
        server.password = "plain-secret"
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")], [server])

        expect:
        with(onlyMirror()) {
            credentials.username == "mirror-user"
            httpHeader == null
        }
    }

    def "encrypted http header value is decrypted with the maven master password"() {
        given:
        securitySettingsFile.text = "<settingsSecurity><master>${ENCRYPTED_MASTER}</master></settingsSecurity>"
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")],
            [serverWithHeaders("corp-mirror", ["Private-Token": ENCRYPTED_PASSWORD])])

        expect:
        onlyMirror().httpHeader.value == "mirror-secret"
    }

    def "malformed http header configuration is ignored"() {
        given:
        def server = server("corp-mirror", null, null)
        server.configuration = Xpp3DomBuilder.build(new StringReader("""
            <configuration>
                <httpHeaders>
                    <property><name>No-Value</name></property>
                </httpHeaders>
            </configuration>
        """))
        settings([mirror("corp-mirror", "*", "https://mirror.example.com/maven2")], [server])

        expect:
        onlyMirror().httpHeader == null
    }

    def "does not print the mirror password or header value in toString"() {
        given:
        settings([mirror("secret-mirror", "*", "https://mirror.example.com/maven2")],
            [server("secret-mirror", "mirror-user", "super-secret")])
        def withHeader = new MavenSettingsProvider.MirrorHttpHeader("Private-Token", "token-secret")

        expect:
        with(onlyMirror().credentials.toString()) {
            contains("mirror-user")
            !contains("super-secret")
        }
        with(withHeader.toString()) {
            contains("Private-Token")
            !contains("token-secret")
        }
    }

    private MavenSettingsProvider.MirroredRepository onlyMirror() {
        def mirrors = provider.getMirrors()
        assert mirrors.size() == 1
        return mirrors[0]
    }

    private void settings(List<Mirror> mirrors, List<Server> servers = []) {
        def settings = new Settings()
        mirrors.each { settings.addMirror(it) }
        servers.each { settings.addServer(it) }
        new DefaultSettingsWriter().write(userSettingsFile, [:], settings)
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
