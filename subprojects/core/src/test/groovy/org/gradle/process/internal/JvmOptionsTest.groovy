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

import org.gradle.api.internal.file.IdentityFileResolver
import org.gradle.process.JavaForkOptions
import spock.lang.Specification

import java.nio.charset.Charset

class JvmOptionsTest extends Specification {
    final String defaultCharset = Charset.defaultCharset().name()

    def "reads options from String"() {
        expect:
        JvmOptions.fromString("") == []
        JvmOptions.fromString("-Xmx512m") == ["-Xmx512m"]
        JvmOptions.fromString("\t-Xmx512m\n") == ["-Xmx512m"]
        JvmOptions.fromString(" -Xmx512m   -Dfoo=bar\n-XDebug  ") == ["-Xmx512m", "-Dfoo=bar", "-XDebug"]
    }

    def "reads quoted options from String"() {
        expect:
        JvmOptions.fromString("-Dfoo=bar -Dfoo2=\"hey buddy\" -Dfoo3=baz") ==
                ["-Dfoo=bar", "-Dfoo2=hey buddy", "-Dfoo3=baz"]

        JvmOptions.fromString("  -Dfoo=\" bar \"  ") == ["-Dfoo= bar "]
        JvmOptions.fromString("  -Dx=\"\"  -Dy=\"\n\" ") == ["-Dx=", "-Dy=\n"]
        JvmOptions.fromString(" \"-Dx= a b c \" -Dy=\" x y z \" ") == ["-Dx= a b c ", "-Dy= x y z "]
    }

    def "understands quoted system properties and jvm opts"() {
        expect:
        parse("  -Dfoo=\" hey man! \"  ").getSystemProperties().get("foo") == " hey man! "
    }

    def "understands 'empty' system properties and jvm opts"() {
        expect:
        parse("-Dfoo= -Dbar -Dbaz=\"\"").getSystemProperties() == [foo: '', bar: '', baz: '']
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
        parse("-Xms1G -Dfile.encoding=UTF-8 -Dfoo.encoding=blah -Dfile.encoding=UTF-16").allJvmArgs == ["-Dfoo.encoding=blah", "-Xms1G", "-Dfile.encoding=UTF-16"]
    }

    def "debug option can be set via allJvmArgs"() {
        setup:
        def opts = createOpts()

        when:
        opts.allJvmArgs = ['-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']
        then:
        opts.debug

        when:
        opts.allJvmArgs = []
        then:
        opts.debug == false
    }

    def "managed jvm args includes heap settings"() {
        expect:
        parse("-Xms1G -XX:-PrintClassHistogram -Xmx2G -Dfoo.encoding=blah").managedJvmArgs == ["-Xms1G", "-Xmx2G", "-Dfile.encoding=${defaultCharset}"]
    }

    def "managed jvm args includes file encoding"() {
        expect:
        parse("-XX:-PrintClassHistogram -Dfile.encoding=klingon-16 -Dfoo.encoding=blah").managedJvmArgs == ["-Dfile.encoding=klingon-16"]
        parse("-XX:-PrintClassHistogram -Dfoo.encoding=blah").managedJvmArgs == ["-Dfile.encoding=${defaultCharset}"]
    }

    def "managed jvm args includes JMX settings"() {
        expect:
        parse("-Dfile.encoding=utf-8 -Dcom.sun.management.jmxremote").managedJvmArgs == ["-Dcom.sun.management.jmxremote", "-Dfile.encoding=utf-8"]
    }

    def "file encoding can be set as systemproperty"() {
        JvmOptions opts = createOpts()
        when:
        opts.systemProperty("file.encoding", "ISO-8859-1")
        then:
        opts.allJvmArgs.contains("-Dfile.encoding=ISO-8859-1");
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
        1 * target.systemProperties({it == ["file.encoding": "UTF-16"]})
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
        opts.jvmArgs(JvmOptions.fromString('-Xmx1G -Xms1G'))
        opts.debug = true
        then:
        opts.allJvmArgs.containsAll(['-Xmx1G', '-Xms1G', '-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005'])
    }

    def "can enter debug mode before setting other options"() {
        def opts = createOpts()
        opts.debug = true
        when:
        opts.jvmArgs(JvmOptions.fromString('-Xmx1G -Xms1G'))
        then:
        opts.allJvmArgs.containsAll(['-Xmx1G', '-Xms1G', '-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005'])
    }

    private JvmOptions createOpts() {
        return new JvmOptions(new IdentityFileResolver())
    }

    private JvmOptions parse(String optsString) {
        def opts = createOpts()
        opts.jvmArgs(JvmOptions.fromString(optsString))
        opts
    }
}
