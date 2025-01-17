/*
 * Copyright 2009 the original author or authors.
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


package org.gradle.process.internal

import org.gradle.api.internal.file.TestFiles
import org.gradle.process.JavaDebugOptions
import org.gradle.process.JavaForkOptions
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.nio.charset.Charset

import static org.gradle.process.internal.JvmOptions.FILE_ENCODING_KEY
import static org.gradle.process.internal.JvmOptions.JAVA_IO_TMPDIR_KEY
import static org.gradle.process.internal.JvmOptions.JMX_REMOTE_KEY
import static org.gradle.process.internal.JvmOptions.USER_COUNTRY_KEY
import static org.gradle.process.internal.JvmOptions.USER_LANGUAGE_KEY
import static org.gradle.process.internal.JvmOptions.USER_VARIANT_KEY
import static org.gradle.process.internal.JvmOptions.fromString

class JvmOptionsTest extends Specification {
    final String defaultCharset = Charset.defaultCharset().name()

    def "reads options from String"() {
        expect:
        fromString("") == []
        fromString("-Xmx512m") == ["-Xmx512m"]
        fromString("\t-Xmx512m\n") == ["-Xmx512m"]
        fromString(" -Xmx512m   -Dfoo=bar\n-XDebug  ") == ["-Xmx512m", "-Dfoo=bar", "-XDebug"]
    }

    def "reads quoted options from String"() {
        expect:
        fromString("-Dfoo=bar -Dfoo2=\"hey buddy\" -Dfoo3=baz") ==
            ["-Dfoo=bar", "-Dfoo2=hey buddy", "-Dfoo3=baz"]

        fromString("  -Dfoo=\" bar \"  ") == ["-Dfoo= bar "]
        fromString("  -Dx=\"\"  -Dy=\"\n\" ") == ["-Dx=", "-Dy=\n"]
        fromString(" \"-Dx= a b c \" -Dy=\" x y z \" ") == ["-Dx= a b c ", "-Dy= x y z "]
    }

    def "understands quoted system properties and jvm opts"() {
        expect:
        parse("  -Dfoo=\" hey man! \"  ").getMutableSystemProperties().get("foo") == " hey man! "
    }

    def "understands 'empty' system properties and jvm opts"() {
        expect:
        parse("-Dfoo= -Dbar -Dbaz=\"\"").getMutableSystemProperties() == [foo: '', bar: '', baz: '']
        parse("-XXfoo=").allJvmArgs.contains('-XXfoo=')
        parse("-XXbar=\"\"").allJvmArgs.contains('-XXbar=')
    }

    def "understands quoted jvm options"() {
        expect:
        parse('  -XX:HeapDumpPath="/tmp/with space" ').jvmArgs.contains('-XX:HeapDumpPath=/tmp/with space')
    }

    def "can parse file encoding property"() {
        expect:
        parse("-Dfile.encoding=UTF-8 -Dfoo.encoding=blah -Dfile.encoding=UTF-16").defaultCharacterEncoding == "UTF-16"
    }

    def "system properties are always before the symbolic arguments"() {
        expect:
        parse("-Xms1G -Dfile.encoding=UTF-8 -Dfoo.encoding=blah -Dfile.encoding=UTF-16").allJvmArgs == ["-Dfoo.encoding=blah", "-Xms1G", "-Dfile.encoding=UTF-16", *localePropertyStrings()]
    }

    def "managed jvm args includes heap settings"() {
        expect:
        parse("-Xms1G -XX:-PrintClassHistogram -Xmx2G -Dfoo.encoding=blah").managedJvmArgs == ["-Xms1G", "-Xmx2G", "-Dfile.encoding=${defaultCharset}", *localePropertyStrings()]
    }

    def "managed jvm args includes file encoding"() {
        expect:
        parse("-XX:-PrintClassHistogram -Dfile.encoding=klingon-16 -Dfoo.encoding=blah").managedJvmArgs == ["-Dfile.encoding=klingon-16", *localePropertyStrings()]
        parse("-XX:-PrintClassHistogram -Dfoo.encoding=blah").managedJvmArgs == ["-Dfile.encoding=${defaultCharset}", *localePropertyStrings()]
    }

    def "managed jvm args includes JMX settings"() {
        expect:
        parse("-Dfile.encoding=utf-8 -Dcom.sun.management.jmxremote").managedJvmArgs == ["-Dcom.sun.management.jmxremote", "-Dfile.encoding=utf-8", *localePropertyStrings()]
    }

    def "file encoding can be set via defaultFileEncoding property"() {
        JvmOptions opts = createOpts()
        when:
        opts.defaultCharacterEncoding = "ISO-8859-1"
        then:
        opts.allJvmArgs.contains("-Dfile.encoding=ISO-8859-1");
    }

    def "uses system default file encoding when null is used"() {
        JvmOptions opts = createOpts()
        when:
        opts.defaultCharacterEncoding = null
        then:
        opts.allJvmArgs.contains("-Dfile.encoding=${defaultCharset}".toString());
    }

    def "last file encoding definition is used"() {
        JvmOptions opts = createOpts()
        when:
        opts.systemProperty("file.encoding", "ISO-8859-1");
        opts.defaultCharacterEncoding = "ISO-8859-2"
        then:
        !opts.allJvmArgs.contains("-Dfile.encoding=ISO-8859-1");
        opts.allJvmArgs.contains("-Dfile.encoding=ISO-8859-2");

        when:
        opts.defaultCharacterEncoding = "ISO-8859-2"
        opts.systemProperty("file.encoding", "ISO-8859-1")
        then:
        !opts.allJvmArgs.contains("-Dfile.encoding=ISO-8859-2")
        opts.allJvmArgs.contains("-Dfile.encoding=ISO-8859-1");
    }

    def "file.encoding arg has default value"() {
        expect:
        createOpts().allJvmArgs.contains("-Dfile.encoding=${defaultCharset}".toString());
    }

    def "copyTo respects defaultFileEncoding"() {
        JavaForkOptions target = Mock(JavaForkOptions)
        when:
        parse("-Dfile.encoding=UTF-8 -Dfoo.encoding=blah -Dfile.encoding=UTF-16").copyTo(target)
        then:
        1 * target.systemProperties({
            it == new TreeMap(["file.encoding": "UTF-16"] + localeProperties())
        })
        1 * target.getDebugOptions() >> TestUtil.newInstance(DefaultJavaDebugOptions)
    }

    def "copyTo copies debugOptions"() {
        JavaDebugOptions debugOptions = TestUtil.newInstance(DefaultJavaDebugOptions);
        JavaForkOptions target = Mock(JavaForkOptions) { it.debugOptions >> debugOptions }
        JvmOptions source = parse("-Dx=y")
        source.debugSpec.host = "*"
        source.debugSpec.port = 1234

        when:
        source.copyTo(target)

        then: "Target should have the debugOptions copied from source"
        target.debugOptions.host.get() == "*"
        target.debugOptions.port.get() == 1234
    }

    def "#propDescr is immutable system property"() {
        when:
        def opts = createOpts()
        opts.jvmArgs(propAsArg)

        then:
        opts.allImmutableJvmArgs.contains(propAsArg.toString())
        and:
        opts.immutableSystemProperties.containsKey(propKey)

        where:
        propDescr                 | propKey                  | propAsArg
        "file encoding"           | FILE_ENCODING_KEY        | "-D${FILE_ENCODING_KEY}=UTF-8"
        "user variant"            | USER_VARIANT_KEY         | "-D${USER_VARIANT_KEY}"
        "user language"           | USER_LANGUAGE_KEY        | "-D${USER_LANGUAGE_KEY}=en"
        "user country"            | USER_COUNTRY_KEY         | "-D${USER_COUNTRY_KEY}=US"
        "jmx remote"              | JMX_REMOTE_KEY           | "-D${JMX_REMOTE_KEY}"
        "temp directory"          | JAVA_IO_TMPDIR_KEY       | "-D${JAVA_IO_TMPDIR_KEY}=/some/tmp/folder"
        "ssl keystore path"       | JvmOptions.SSL_KEYSTORE_KEY         | "-D${JvmOptions.SSL_KEYSTORE_KEY}=/keystore/path"
        "ssl keystore password"   | JvmOptions.SSL_KEYSTOREPASSWORD_KEY | "-D${JvmOptions.SSL_KEYSTOREPASSWORD_KEY}=secret"
        "ssl keystore type"       | JvmOptions.SSL_KEYSTORETYPE_KEY     | "-D${JvmOptions.SSL_KEYSTORETYPE_KEY}=jks"
        "ssl truststore path"     | JvmOptions.SSL_TRUSTSTORE_KEY       | "-D${JvmOptions.SSL_TRUSTSTORE_KEY}=truststore/path"
        "ssl truststore password" | JvmOptions.SSL_TRUSTPASSWORD_KEY    | "-D${JvmOptions.SSL_TRUSTPASSWORD_KEY}=secret"
        "ssl truststore type"     | JvmOptions.SSL_TRUSTSTORETYPE_KEY   | "-D${JvmOptions.SSL_TRUSTSTORETYPE_KEY}=jks"
    }

    def "#propDescr can be set as systemproperty"() {
        JvmOptions opts = createOpts()
        when:
        opts.systemProperty(propKey, propValue)
        then:
        opts.allJvmArgs.contains("-D${propKey}=${propValue}".toString());
        where:
        propDescr                 | propKey                  | propValue
        "file encoding"           | FILE_ENCODING_KEY        | "ISO-8859-1"
        "user country"            | USER_COUNTRY_KEY         | "en"
        "user language"           | USER_LANGUAGE_KEY        | "US"
        "temp directory"          | JAVA_IO_TMPDIR_KEY       | "/some/tmp/folder"
        "ssl keystore path"       | JvmOptions.SSL_KEYSTORE_KEY         | "/keystore/path"
        "ssl keystore password"   | JvmOptions.SSL_KEYSTOREPASSWORD_KEY | "secret"
        "ssl keystore type"       | JvmOptions.SSL_KEYSTORETYPE_KEY     | "jks"
        "ssl truststore path"     | JvmOptions.SSL_TRUSTSTORE_KEY       | "truststore/path"
        "ssl truststore password" | JvmOptions.SSL_TRUSTPASSWORD_KEY    | "secret"
        "ssl truststore type"     | JvmOptions.SSL_TRUSTSTORETYPE_KEY   | "jks"
    }

    def "can enter debug mode"() {
        def opts = createOpts()
        when:
        opts.debug = true
        then:
        opts.debug
    }

    def "can enter debug mode after setting other options"() {
        def opts = createOpts()
        when:
        opts.jvmArgs(fromString('-Xmx1G -Xms1G'))
        opts.debug = true
        then:
        opts.allJvmArgs.containsAll(['-Xmx1G', '-Xms1G', '-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005'])
    }

    def "can enter debug mode before setting other options"() {
        def opts = createOpts()
        opts.debug = true
        when:
        opts.jvmArgs(fromString('-Xmx1G -Xms1G'))
        then:
        opts.allJvmArgs.containsAll(['-Xmx1G', '-Xms1G', '-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005'])
    }

    def "can configure debug mode"(port, server, suspend, expected) {
        setup:
        def opts = createOpts()

        when:
        opts.debug = true
        opts.debugSpec.port = port
        opts.debugSpec.server = server
        opts.debugSpec.suspend = suspend

        then:
        opts.allJvmArgs.findAll { it.contains 'jdwp' } == [expected]

        where:
        port | server | suspend | expected
        1122 | false  | false   | '-agentlib:jdwp=transport=dt_socket,server=n,suspend=n,address=1122'
        1123 | false  | true    | '-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=1123'
        1124 | true   | false   | '-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1124'
        1125 | true   | true    | '-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=1125'
    }

    def "options with newlines are parsed correctly"() {
        def opts = createOpts()
        when:
        opts.jvmArgs('-Dprops=a:1\nb:2\nc:3')

        then:
        opts.allJvmArgs.contains('-Dprops=a:1\nb:2\nc:3')
        opts.mutableSystemProperties['props'] == 'a:1\nb:2\nc:3'
    }

    def "options with Win newlines are parsed correctly"() {
        def opts = createOpts()
        when:
        opts.jvmArgs('-Dprops=a:1\r\nb:2\r\nc:3')

        then:
        opts.allJvmArgs.contains('-Dprops=a:1\r\nb:2\r\nc:3')
        opts.mutableSystemProperties['props'] == 'a:1\r\nb:2\r\nc:3'
    }

    private JvmOptions createOpts() {
        return new JvmOptions(TestFiles.fileCollectionFactory())
    }

    private JvmOptions parse(String optsString) {
        def opts = createOpts()
        opts.jvmArgs(fromString(optsString))
        opts
    }

    private static List<String> localePropertyStrings(Locale locale = Locale.default) {
        localeProperties(locale).collect {
            it.value ? "-D$it.key=$it.value" : "-D$it.key"
        }*.toString()
    }

    private static Map<String, String> localeProperties(Locale locale = Locale.default) {
        ["country", "language", "variant"].sort().collectEntries {
            ["user.$it".toString(), locale."$it".toString()]
        }
    }

}
