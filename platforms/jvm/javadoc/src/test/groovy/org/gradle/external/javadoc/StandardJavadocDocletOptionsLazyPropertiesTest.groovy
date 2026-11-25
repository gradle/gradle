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

package org.gradle.external.javadoc

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.provider.Provider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class StandardJavadocDocletOptionsLazyPropertiesTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    private StandardJavadocDocletOptions newOptions() {
        TestUtil.objectFactory(temporaryFolder.testDirectory).newInstance(StandardJavadocDocletOptions)
    }

    private static Object lazyValue(StandardJavadocDocletOptions options, String name) {
        def property = options.lazyProperties."$name"
        if (property instanceof FileSystemLocationProperty) {
            return property.asFile.getOrNull()
        }
        if (property instanceof ConfigurableFileCollection) {
            return property.files as List
        }
        (property as Provider).getOrNull()
    }

    private static final File A = new File("/abs/a").absoluteFile
    private static final File B = new File("/abs/b").absoluteFile
    private static final JavadocOfflineLink LINK = new JavadocOfflineLink("https://example.org", "/abs/list")

    def "#name: the setter and the fluent method write the lazy property and the getter reads it"() {
        def options = newOptions()

        when:
        options."$name" = value

        then:
        options."$name" == value
        lazyValue(options, name) == value

        when:
        options = newOptions()
        fluent.call(options)

        then:
        options."$name" == fluentValue
        lazyValue(options, name) == fluentValue

        where:
        name                    | value                                   | fluent                                            | fluentValue
        "overview"              | "overview.html"                         | { it.overview("o.html") }                         | "o.html"
        "memberLevel"           | JavadocMemberLevel.PACKAGE              | { it.showFromPrivate() }                          | JavadocMemberLevel.PRIVATE
        "doclet"                | "org.example.Doclet"                    | { it.doclet("org.example.Other") }                | "org.example.Other"
        "docletpath"            | [A]                                     | { it.docletpath(A, B) }                           | [A, B]
        "source"                | "17"                                    | { it.source("21") }                               | "21"
        "classpath"             | [A]                                     | { it.classpath(A).classpath([B]) }                | [A, B]
        "modulePath"            | [A]                                     | { it.modulePath([A, B]) }                         | [A, B]
        "bootClasspath"         | [A]                                     | { it.bootClasspath(A, B) }                        | [A, B]
        "extDirs"               | [A]                                     | { it.extDirs(A, B) }                              | [A, B]
        "outputLevel"           | JavadocOutputLevel.VERBOSE              | { it.verbose() }                                  | JavadocOutputLevel.VERBOSE
        "breakIterator"         | true                                    | { it.breakIterator() }                            | true
        "locale"                | "en"                                    | { it.locale("fr") }                               | "fr"
        "encoding"              | "UTF-8"                                 | { it.encoding("ISO-8859-1") }                     | "ISO-8859-1"
        "JFlags"                | ["-Xmx64m"]                             | { it.jFlags("-Xmx64m", "-Xss1m") }                | ["-Xmx64m", "-Xss1m"]
        "optionFiles"           | [A]                                     | { it.optionFiles(A, B) }                          | [A, B]
        "destinationDirectory"  | A                                       | { it.destinationDirectory(B) }                    | B
        "use"                   | true                                    | { it.use() }                                      | true
        "version"               | true                                    | { it.version() }                                 | true
        "author"                | true                                    | { it.author() }                                  | true
        "splitIndex"            | true                                    | { it.splitIndex() }                              | true
        "windowTitle"           | "window"                                | { it.windowTitle("w") }                           | "w"
        "header"                | "header"                                | { it.header("h") }                                | "h"
        "docTitle"              | "doc"                                   | { it.docTitle("d") }                              | "d"
        "footer"                | "footer"                                | { it.footer("f") }                                | "f"
        "bottom"                | "bottom"                                | { it.bottom("b") }                                | "b"
        "links"                 | ["https://a.org"]                       | { it.links("https://a.org", "https://b.org") }    | ["https://a.org", "https://b.org"]
        "linksOffline"          | [LINK]                                  | { it.linksOffline("https://example.org", "/abs/list") } | [LINK]
        "linkSource"            | true                                    | { it.linkSource() }                               | true
        "groups"                | [g: ["a.*"]]                            | { it.group("g", "a.*", "b.*") }                   | [g: ["a.*", "b.*"]]
        "noDeprecated"          | true                                    | { it.noDeprecated() }                             | true
        "noDeprecatedList"      | true                                    | { it.noDeprecatedList() }                         | true
        "noSince"               | true                                    | { it.noSince() }                                  | true
        "noTree"                | true                                    | { it.noTree() }                                   | true
        "noIndex"               | true                                    | { it.noIndex() }                                  | true
        "noHelp"                | true                                    | { it.noHelp() }                                   | true
        "noNavBar"              | true                                    | { it.noNavBar() }                                 | true
        "helpFile"              | A                                       | { it.helpFile(B) }                                | B
        "stylesheetFile"        | A                                       | { it.stylesheetFile(B) }                          | B
        "serialWarn"            | true                                    | { it.serialWarn() }                               | true
        "charSet"               | "UTF-8"                                 | { it.charSet("ISO-8859-1") }                      | "ISO-8859-1"
        "docEncoding"           | "UTF-8"                                 | { it.docEncoding("ISO-8859-1") }                  | "ISO-8859-1"
        "keyWords"              | true                                    | { it.keyWords() }                                 | true
        "tags"                  | ["todo:a:To Do:"]                       | { it.tags("a", "b") }                             | ["a", "b"]
        "taglets"               | ["org.example.Taglet"]                  | { it.taglets("a", "b") }                          | ["a", "b"]
        "tagletPath"            | [A]                                     | { it.tagletPath(A, B) }                           | [A, B]
        "docFilesSubDirs"       | true                                    | { it.docFilesSubDirs() }                          | true
        "excludeDocFilesSubDir" | ["a"]                                   | { it.excludeDocFilesSubDir("a", "b") }            | ["a", "b"]
        "noQualifiers"          | ["java.lang"]                           | { it.noQualifiers("a", "b") }                     | ["a", "b"]
        "noTimestamp"           | false                                   | { it.noTimestamp(false) }                         | false
        "noComment"             | true                                    | { it.noComment() }                                | true
    }

    def "#name: setting null reads #expected"() {
        def options = newOptions()
        options."$name" = value

        when:
        options."$name" = null

        then:
        options."$name" == expected

        where:
        name                    | value                      | expected
        "overview"              | "overview.html"            | null
        "memberLevel"           | JavadocMemberLevel.PUBLIC  | null
        "doclet"                | "org.example.Doclet"       | null
        "source"                | "17"                       | null
        "locale"                | "en"                       | null
        "encoding"              | "UTF-8"                    | null
        "outputLevel"           | JavadocOutputLevel.VERBOSE | null
        "destinationDirectory"  | A                          | null
        "windowTitle"           | "window"                   | null
        "header"                | "header"                   | null
        "docTitle"              | "doc"                      | null
        "footer"                | "footer"                   | null
        "bottom"                | "bottom"                   | null
        "helpFile"              | A                          | null
        "stylesheetFile"        | A                          | null
        "charSet"               | "UTF-8"                    | null
        "docEncoding"           | "UTF-8"                    | null
        "JFlags"                | ["-Xmx64m"]                | null
        "linksOffline"          | [LINK]                     | null
        "groups"                | [g: ["a.*"]]               | null
        "docletpath"            | [A]                        | []
        "classpath"             | [A]                        | []
        "modulePath"            | [A]                        | []
        "bootClasspath"         | [A]                        | []
        "extDirs"               | [A]                        | []
        "tagletPath"            | [A]                        | []
        "links"                 | ["https://a.org"]          | []
        "tags"                  | ["a"]                      | []
        "taglets"               | ["a"]                      | []
        "excludeDocFilesSubDir" | ["a"]                      | []
        "noQualifiers"          | ["a"]                      | []
        // On master the getter returned null; a file collection has no null state
        "optionFiles"           | [A]                        | []
    }

    def "the list getters are live views"() {
        def options = newOptions()

        when:
        options.links.add("https://a.org")
        options.classpath.add(A)
        options.classpath.add(0, B)
        options.JFlags.add("-Xmx64m")
        options.tagletPath.addAll([A, B])
        options.tagletPath.remove(A)

        then:
        options.links == ["https://a.org"]
        options.classpath == [B, A]
        options.JFlags == ["-Xmx64m"]
        options.tagletPath == [B]
    }

    def "a list can be set to its own view"() {
        def options = newOptions()
        options.links("https://a.org")
        options.classpath(A)

        when:
        options.links = options.links
        options.classpath = options.classpath
        options.links("https://b.org")
        options.classpath(B)

        then:
        options.links == ["https://a.org", "https://b.org"]
        options.classpath == [A, B]
    }

    def "the lists of groups can be changed through the map getter"() {
        def options = newOptions()

        when:
        options.group("x", ["a.*"])
        options.groups.get("x").add("b.*")
        options.groups.put("y", ["c.*"])

        then:
        options.groups == [x: ["a.*", "b.*"], y: ["c.*"]]
        options.lazyProperties.groups.get() == [x: ["a.*", "b.*"], y: ["c.*"]]
    }

    def "the noTimestamp field reads and writes the lazy property"() {
        def options = newOptions()

        expect:
        options.@noTimestamp.value
        options.lazyProperties.noTimestamp.get()

        when:
        options.@noTimestamp.value = false

        then:
        !options.isNoTimestamp()
        !options.lazyProperties.noTimestamp.get()
    }

    def "copy copies all values into separate properties"() {
        def options = newOptions()
        options.overview("o.html").showFromPackage().doclet("D").docletpath(A).source("17").classpath(A).modulePath([B])
            .bootClasspath(A).extDirs(B).verbose().breakIterator().locale("en").encoding("UTF-8").jFlags("-Xmx64m").optionFiles(A)
        options.destinationDirectory(A).use().version().author().splitIndex().windowTitle("w").header("h").docTitle("d")
            .footer("f").bottom("b").links("https://a.org").linksOffline("https://example.org", "/abs/list").linkSource()
            .group("g", ["a.*"]).noDeprecated().noDeprecatedList().noSince().noTree().noIndex().noHelp().noNavBar()
            .helpFile(A).stylesheetFile(B).serialWarn().charSet("UTF-8").docEncoding("UTF-8").keyWords().tags("t").taglets("T")
            .tagletPath(B).docFilesSubDirs().excludeDocFilesSubDir("x").noQualifiers("java.lang").noTimestamp(false).noComment()
        options.addStringOption("extra", "value")
        options.sourceNames("A.java")

        when:
        def copy = newOptions().copy(options)

        then:
        names.every { copy."$it" == options."$it" }
        names.every { !copy.lazyProperties."$it".is(options.lazyProperties."$it") }
        copy.sourceNames == ["A.java"]
        copy.extraOptions == options.extraOptions

        when:
        copy.links("https://b.org")
        copy.groups.get("g").add("b.*")
        copy.classpath(B)
        copy.jFlags("-Xss1m")
        copy.windowTitle("other")

        then:
        options.links == ["https://a.org"]
        options.groups == [g: ["a.*"]]
        options.classpath == [A]
        options.JFlags == ["-Xmx64m"]
        options.windowTitle == "w"

        where:
        names = ["overview", "memberLevel", "doclet", "docletpath", "source", "classpath", "modulePath", "bootClasspath", "extDirs",
                 "outputLevel", "breakIterator", "locale", "encoding", "JFlags", "optionFiles", "destinationDirectory", "use", "version",
                 "author", "splitIndex", "windowTitle", "header", "docTitle", "footer", "bottom", "links", "linksOffline", "linkSource",
                 "groups", "noDeprecated", "noDeprecatedList", "noSince", "noTree", "noIndex", "noHelp", "noNavBar", "helpFile",
                 "stylesheetFile", "serialWarn", "charSet", "docEncoding", "keyWords", "tags", "taglets", "tagletPath", "docFilesSubDirs",
                 "excludeDocFilesSubDir", "noQualifiers", "noTimestamp", "noComment"]
    }
}
