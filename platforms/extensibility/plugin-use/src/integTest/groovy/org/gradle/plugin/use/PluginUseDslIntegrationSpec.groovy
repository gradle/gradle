/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugin.use

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.GradleVersion

import static org.gradle.plugin.use.internal.DefaultPluginId.*
import static org.gradle.plugin.use.internal.PluginRequestCollector.EMPTY_VALUE
import static org.gradle.plugin.use.internal.PluginUseScriptBlockMetadataCompiler.*
import static org.hamcrest.CoreMatchers.containsString

class PluginUseDslIntegrationSpec extends AbstractIntegrationSpec {

    def "can use plugins block in project build scripts"() {
        when:
        buildScript """
          plugins {
            id "java"
            id "noop" version "1.0"
          }
        """

        then:
        succeeds "help"
    }

    def "buildscript blocks are allowed before plugin statements"() {
        when:
        buildScript """
            buildscript {}
            plugins {}
        """

        then:
        succeeds "help"
    }

    def "buildscript blocks are not allowed after plugin blocks"() {
        when:
        buildScript """
            plugins {}
            buildscript {}
        """

        then:
        fails "help"

        and:
        failure.assertHasLineNumber 3
        failure.assertHasFileName("Build file '${buildFile}'")
        failure.assertThatCause(containsString("all buildscript {} blocks must appear before any plugins {} blocks"))
        includesLinkToUserguide()
    }

    void includesLinkToUserguide() {
        failure.assertThatCause(containsString("https://docs.gradle.org/${GradleVersion.current().getVersion()}/userguide/plugins.html#sec:plugins_block"))
    }

    def "build logic cannot precede plugins block"() {
        when:
        buildScript """
            someThing()
            plugins {}
        """

        then:
        fails "help"

        and:
        failure.assertHasLineNumber 3
        failure.assertHasFileName("Build file '${buildFile}'")
        failure.assertThatCause(containsString("only buildscript {}, pluginManagement {} and other plugins {} script blocks are allowed before plugins {} blocks, no other statements are allowed"))
        includesLinkToUserguide()
    }

    def "build logic cannot precede any plugins block"() {
        when:
        buildScript """
            plugins {}
            someThing()
            plugins {}
        """

        then:
        fails "help"

        and:
        failure.assertHasLineNumber 4
        failure.assertHasFileName("Build file '${buildFile}'")
        failure.assertThatCause(containsString("only buildscript {}, pluginManagement {} and other plugins {} script blocks are allowed before plugins {} blocks, no other statements are allowed"))
        includesLinkToUserguide()
    }

    def "settings scripts can have plugin blocks"() {
        when:
        settingsFile.text = """
          plugins {
            id "noop" version "1.0"
          }
        """

        then:
        succeeds "help"
    }

    def "init scripts cannot have plugin blocks"() {
        def initScript = file("init.gradle")

        when:
        initScript << "plugins {}"

        then:
        args "-I", initScript.absolutePath
        fails "help"

        failure.assertHasLineNumber 1
        failure.assertHasFileName("Initialization script '$initScript.absolutePath'")
        failure.assertThatCause(containsString("Only Project and Settings build scripts can contain plugins {} blocks"))
        includesLinkToUserguide()
    }

    def "script plugins cannot have plugin blocks"() {
        def scriptPlugin = file("plugin.gradle")

        when:
        scriptPlugin << "plugins {}"
        buildScript "apply from: 'plugin.gradle'"

        then:
        fails "help"

        failure.assertHasLineNumber 1
        failure.assertHasFileName("Script '$scriptPlugin.absolutePath'")
        failure.assertThatCause(containsString("Only Project and Settings build scripts can contain plugins {} blocks"))
        includesLinkToUserguide()
    }

    def "script plugins applied to arbitrary objects cannot have plugin blocks"() {
        def scriptPlugin = file("plugin.gradle")

        when:
        scriptPlugin << "plugins {}"
        buildScript "task foo; apply from: 'plugin.gradle', to: foo"

        then:
        fails "help"

        failure.assertHasLineNumber 1
        failure.assertHasFileName("Script '$scriptPlugin.absolutePath'")
        failure.assertThatCause(containsString("Only Project and Settings build scripts can contain plugins {} blocks"))
        includesLinkToUserguide()
    }

    def "illegal syntax in plugins block - #code"() {
        when:
        buildScript("""plugins {\n$code\n}""")

        then:
        fails "help"
        failure.assertHasLineNumber lineNumber
        failure.assertHasFileName("Build file '${buildFile}'")
        failure.assertThatCause(containsString(msg))
        includesLinkToUserguide()

        where:
        lineNumber | code                                   | msg
        2          | "a"                                    | BASE_MESSAGE
        2          | "def a = null"                         | BASE_MESSAGE
        2          | "def a = id('foo')"                    | BASE_MESSAGE
        2          | "delegate.id('a')"                     | BASE_MESSAGE
        2          | "id()"                                 | NEED_LITERAL_STRING
        2          | "id(1)"                                | NEED_LITERAL_STRING
        2          | "id(System.getProperty('foo'))"        | NEED_LITERAL_STRING
        2          | "id('a' + 'b')"                        | NEED_LITERAL_STRING
        2          | "id(\"\${'foo'}\")"                    | NEED_LITERAL_STRING
        2          | "version('foo')"                       | BASE_MESSAGE
        2          | "id('foo').version(1)"                 | NEED_INTERPOLATED_STRING
        2          | "id 'foo' version 1"                   | NEED_INTERPOLATED_STRING
        2          | "id 'foo' version \"\${foo.bar}\""     | NEED_INTERPOLATED_STRING
        2          | "id 'foo' bah '1'"                     | EXTENDED_MESSAGE
        2          | "foo 'foo' version '1'"                | BASE_MESSAGE
        3          | "id('foo')\nfoo 'bar'"                 | BASE_MESSAGE
        2          | "if (true) id 'foo'"                   | BASE_MESSAGE
        2          | "id 'foo';version 'bar'"               | BASE_MESSAGE
        2          | "apply false"                          | BASE_MESSAGE
        2          | "id 'foo' apply"                       | BASE_MESSAGE
        2          | "id 'foo' apply('foo')"                | NEED_SINGLE_BOOLEAN
        2          | "apply plugin: 'java'"                 | NEED_SINGLE_BOOLEAN
        2          | "id null"                              | NEED_LITERAL_STRING
        2          | "id 'foo' version null"                | NEED_INTERPOLATED_STRING
        2          | "file('foo')" /* script api */         | BASE_MESSAGE
        2          | "getVersion()" /* script target api */ | BASE_MESSAGE
    }

    def "allowed syntax in plugins block - #code"() {
        given:
        when:
        buildScript("""plugins {\n$code\n}""")

        then:
        succeeds "help"

        where:
        code << [
                "id('noop')",
                "id 'noop'",
                "id('noop').version('bar')",
                "id 'noop' version 'bar'",
                "id('noop').\nversion 'bar'",
                "id('java');id('noop')",
                "id('java')\nid('noop')",
                "id('noop').version('bar');id('java')",
                "id('noop').version('bar')\nid('java')",
                "id 'noop' apply false",
                "id('noop').apply(false)",
                "id('noop').apply(false);id('java')",
                "id 'noop' version 'bar' apply false",
                "id('noop').version('bar').apply(false)",
        ]
    }

    def "illegal value in plugins block - #code"() {
        when:
        buildScript("""plugins {\n$code\n}""")

        then:
        fails "help"
        failure.assertHasLineNumber lineNumber
        failure.assertHasFileName("Build file '${buildFile}'")
        failure.assertThatCause(containsString(msg))

        where:
        lineNumber | code                                   | msg
        2          | "id ' '"                               | invalidPluginIdCharMessage(' ' as char)
        2          | "id '\$'"                              | invalidPluginIdCharMessage('$' as char)
        2          | "id ''"                                | EMPTY_VALUE
        2          | "id '.foo'"                            | ID_SEPARATOR_ON_START_OR_END
        2          | "id 'foo.'"                            | ID_SEPARATOR_ON_START_OR_END
        2          | "id '.'"                               | ID_SEPARATOR_ON_START_OR_END
        2          | "id 'foo..bar'"                        | DOUBLE_SEPARATOR
        2          | "id 'foo' version ''"                  | EMPTY_VALUE
    }

    def "can interpolate properties in project plugins block"() {
        when:
        file("gradle.properties") << """
    foo = 333
    bar = 444
    foo.bar = 555
"""
        buildScript("""plugins {\n$code\n}""")

        then:
        succeeds "help"

        where:
        code << [
                'id("noop").version("${foo}")',
                'id("noop").version("0.${foo}")',
                'id("noop").version("${foo}.0")',
                'id("noop").version("0.${foo}.1")',
        ]
    }

    def "can interpolate properties in settings plugins block"() {
        when:
        file("gradle.properties") << """
    foo = 333
    bar = 444
    foo.bar = 555
"""
        settingsFile.text = """
            plugins {\n$code\n}
        """

        then:
        succeeds "help"

        where:
        code << [
            'id("noop").version("${foo}")',
            'id("noop").version("0.${foo}")',
            'id("noop").version("${foo}.0")',
            'id("noop").version("0.${foo}.1")',
        ]
    }

    def "fails to interpolate unknown property in project plugins block"() {
        when:
        buildScript("""plugins {\n$code\n}""")

        then:
        fails "help"
        failure.assertHasLineNumber lineNumber
        failure.assertHasFileName("Build file '${buildFile}'")
        failure.assertThatCause(containsString(msg))

        where:
        lineNumber | code                                         | msg
        2          | 'id("noop").version("${unknown}")'           | "Could not get unknown property 'unknown'"
        2          | 'id("noop").version("part-${unknown}-part")' | "Could not get unknown property 'unknown'"
    }

    def "fails to interpolate unknown property in settings plugins block"() {
        when:
        settingsFile.text = """
            plugins {\n$code\n}
        """

        then:
        fails "help"
        failure.assertHasLineNumber lineNumber
        failure.assertHasFileName("Settings file '${settingsFile}'")
        failure.assertThatCause(containsString(msg))

        where:
        lineNumber | code                                         | msg
        3          | 'id("noop").version("${unknown}")'           | "Could not get unknown property 'unknown'"
        3          | 'id("noop").version("part-${unknown}-part")' | "Could not get unknown property 'unknown'"
    }


}
