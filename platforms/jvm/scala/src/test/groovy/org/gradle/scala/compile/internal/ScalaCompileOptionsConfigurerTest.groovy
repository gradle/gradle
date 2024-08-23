/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.scala.compile.internal

import org.gradle.api.internal.tasks.scala.MinimalScalaCompileOptions
import org.gradle.api.tasks.scala.ScalaCompileOptions
import org.gradle.api.tasks.scala.internal.ScalaCompileOptionsConfigurer
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.internal.JavaToolchain
import org.gradle.language.scala.tasks.KeepAliveMode
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Subject

@Subject(ScalaCompileOptionsConfigurer)
class ScalaCompileOptionsConfigurerTest extends Specification {

    def 'using Java #toolchain and Scala #scalaLibraryVersion results in #expectedTarget'() {
        given:
        ScalaCompileOptions scalaCompileOptions = newInitializedScalaCompileOptions()
        scalaCompileOptions.additionalParameters = ["-some-other-flag"].asImmutable()
        def isScala3 = scalaLibraryVersion.startsWith("3.")
        File scalaLibrary = new File(isScala3 ? "scala3-library_3-${scalaLibraryVersion}.jar" : "scala-library-${scalaLibraryVersion}.jar")
        Set<File> classpath = [scalaLibrary]

        when:
        def minimalCompileOptions = new MinimalScalaCompileOptions(scalaCompileOptions)
        ScalaCompileOptionsConfigurer.configure(minimalCompileOptions, scalaCompileOptions, createToolchain(javaToolchain, fallbackToolchain), classpath)

        then:
        minimalCompileOptions.additionalParameters.last() == expectedTarget

        where:
        javaToolchain | fallbackToolchain | scalaLibraryVersion | expectedTarget
        6             | false             | '2.10.0'            | '-target:jvm-1.6'
        7             | false             | '2.10.0'            | '-target:jvm-1.7'
        6             | false             | '2.11.0'            | '-target:jvm-1.6'
        7             | false             | '2.11.0'            | '-target:jvm-1.7'

        8             | false             | '2.10.0'            | '-target:jvm-1.8'
        8             | false             | '2.11.0'            | '-target:jvm-1.8'
        8             | false             | '2.11.12'           | '-target:jvm-1.8'
        8             | false             | '2.12.0'            | '-target:jvm-1.8'
        8             | false             | '2.12.14'           | '-target:jvm-1.8'
        8             | false             | '2.13.0'            | '-target:jvm-1.8'
        8             | true              | '2.13.0'            | '-target:jvm-1.8'

        11            | true              | '2.11.12'           | '-target:jvm-1.8'
        11            | true              | '2.12.0'            | '-target:jvm-1.8'
        11            | true              | '2.12.14'           | '-target:jvm-1.8'
        11            | true              | '2.13.0'            | '-target:jvm-1.8'

        11            | false             | '2.11.12'           | '-target:jvm-1.11'
        11            | false             | '2.12.0'            | '-target:jvm-1.11'
        11            | false             | '2.12.14'           | '-target:jvm-1.11'
        11            | false             | '2.13.0'            | '-target:jvm-1.11'

        8             | false             | '2.13.1'            | '-target:8'
        9             | false             | '2.13.1'            | '-target:9'
        11            | false             | '2.13.1'            | '-target:11'
        17            | false             | '2.13.1'            | '-target:17'
        17            | false             | '2.13.2'            | '-target:17'

        8             | true              | '2.13.1'            | '-target:8'
        11            | true              | '2.13.1'            | '-target:8'
        17            | true              | '2.13.1'            | '-target:8'
        17            | true              | '2.13.2'            | '-target:8'

        17            | false             | '2.13.9'            | '-release:17'
        17            | false             | '2.13.10'           | '-release:17'
        17            | false             | '3.2.1'             | '-release:17'

        17            | true              | '2.13.9'            | '-target:8'
        17            | true              | '2.13.10'           | '-target:8'
        17            | true              | '3.2.1'             | '-Xtarget:8'

        toolchain = fallbackToolchain ? "$javaToolchain (fallback)" : javaToolchain
    }

    def 'does not configure target jvm if toolchain is not present'() {
        given:
        ScalaCompileOptions scalaCompileOptions = newInitializedScalaCompileOptions()
        File scalaLibrary = new File("scala-library-2.11.0.jar")
        Set<File> classpath = [scalaLibrary]

        when:
        def minimalCompileOptions = new MinimalScalaCompileOptions(scalaCompileOptions)
        ScalaCompileOptionsConfigurer.configure(minimalCompileOptions, scalaCompileOptions, null, classpath)

        then:
        !minimalCompileOptions.additionalParameters
    }

    def 'does not configure target jvm if scala library is not present or invalid'() {
        given:
        ScalaCompileOptions scalaCompileOptions = newInitializedScalaCompileOptions()
        File scalaLibrary = new File("scala-invalid-2.11.0.jar")
        Set<File> classpath = [scalaLibrary]

        when:
        def minimalCompileOptions = new MinimalScalaCompileOptions(scalaCompileOptions)
        ScalaCompileOptionsConfigurer.configure(minimalCompileOptions, scalaCompileOptions, createToolchain(8, false), classpath)

        then:
        !minimalCompileOptions.additionalParameters

        where:
        scalaFileName << [
            "scala-something-else-2.10.0.jar",
            "scala-library.jar",
        ]
    }

    def 'does not configure target jvm if scala compiler already has a configured target via #targetFlagName flag'() {
        given:
        ScalaCompileOptions scalaCompileOptions = newInitializedScalaCompileOptions()
        scalaCompileOptions.additionalParameters = targetFlagParts.toList()
        Set<File> classpath = [new File("scala-library-2.13.1.jar")]

        when:
        def minimalCompileOptions = new MinimalScalaCompileOptions(scalaCompileOptions)
        ScalaCompileOptionsConfigurer.configure(minimalCompileOptions, scalaCompileOptions, createToolchain(17, false), classpath)

        then:
        minimalCompileOptions.additionalParameters.find { it == targetFlagParts[0] }
        minimalCompileOptions.additionalParameters.find { it.contains("17") } == null

        where:
        targetFlagParts                          | _
        ['-target:8']                            | _
        ['--target:8']                           | _
        ['-target', '8']                         | _
        ['-release:8']                           | _
        ['--release:8']                          | _
        ['-release', '8']                        | _
        ['--release', '8']                       | _
        ['-java-output-version:8']               | _
        ['-java-output-version', '8']            | _
        ['-Xtarget:8']                           | _
        ['-Xtarget', '8']                        | _
        ['--Xtarget:8']                          | _
        ['--Xtarget', '8']                       | _
        ['-Xunchecked-java-output-version:8']    | _
        ['-Xunchecked-java-output-version', '8'] | _

        targetFlagName = targetFlagParts[0]
    }

    private JavaToolchain createToolchain(int javaVersion, boolean isFallback) {
        return Mock(JavaToolchain) {
            getLanguageVersion() >> JavaLanguageVersion.of(javaVersion)
            isFallbackToolchain() >> isFallback
        }
    }

    private static ScalaCompileOptions newInitializedScalaCompileOptions() {
        def scalaCompileOptions = TestUtil.newInstance(ScalaCompileOptions)
        scalaCompileOptions.keepAliveMode = KeepAliveMode.SESSION
        return scalaCompileOptions
    }
}
