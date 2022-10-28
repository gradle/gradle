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

import org.gradle.api.file.Directory
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.tasks.ScalaRuntime
import org.gradle.api.tasks.scala.ScalaCompileOptions
import org.gradle.api.tasks.scala.internal.ScalaCompileOptionsConfigurer
import org.gradle.jvm.toolchain.JavaInstallationMetadata
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Subject

@Subject(ScalaCompileOptionsConfigurer)
class ScalaCompileOptionsConfigurerTest extends Specification {

    private final ScalaRuntime scalaRuntime = Mock(ScalaRuntime)
    private final VersionParser versionParser = new VersionParser()

    def 'configuring target jvm for JVM #javaVersion and Scala #scalaLibraryVersion results in #expectedTarget'() {
        given:
        ScalaCompileOptions scalaCompileOptions = TestUtil.objectFactory().newInstance(ScalaCompileOptions)
        File scalaLibrary = new File("scala-library-${scalaLibraryVersion}.jar")
        Set<File> classpath = [scalaLibrary]

        when:
        ScalaCompileOptionsConfigurer.configure(scalaCompileOptions, createToolchain(javaVersion), classpath, versionParser)

        then:
        !scalaCompileOptions.additionalParameters.empty
        scalaCompileOptions.additionalParameters.find { it == expectedTarget }

        where:
        javaVersion | scalaLibraryVersion | expectedTarget
        6           | '2.10.0'            | '-target:jvm-1.6'
        7           | '2.10.0'            | '-target:jvm-1.7'
        6           | '2.11.0'            | '-target:jvm-1.6'
        7           | '2.11.0'            | '-target:jvm-1.7'
        8           | '2.10.0'            | '-target:jvm-1.8'
        8           | '2.11.0'            | '-target:jvm-1.8'
        8           | '2.11.12'           | '-target:jvm-1.8'
        8           | '2.12.0'            | '-target:jvm-1.8'
        8           | '2.12.14'           | '-target:jvm-1.8'
        8           | '2.13.0'            | '-target:jvm-1.8'
        11          | '2.11.12'           | '-target:jvm-1.11'
        11          | '2.12.0'            | '-target:jvm-1.11'
        11          | '2.12.14'           | '-target:jvm-1.11'
        11          | '2.13.0'            | '-target:jvm-1.11'
        8           | '2.13.1'            | '-target:8'
        9           | '2.13.1'            | '-target:9'
        11          | '2.13.1'            | '-target:11'
        17          | '2.13.1'            | '-target:17'
    }

    def 'does not configure target jvm if toolchain is not present'() {
        given:
        ScalaCompileOptions scalaCompileOptions = TestUtil.objectFactory().newInstance(ScalaCompileOptions)
        File scalaLibrary = new File("scala-library-2.11.0.jar")
        Set<File> classpath = [scalaLibrary]

        when:
        ScalaCompileOptionsConfigurer.configure(scalaCompileOptions, null, classpath, getVersionParser())

        then:
        !scalaCompileOptions.additionalParameters
    }

    def 'does not configure target jvm if scala library is not present or invalid'() {
        given:
        ScalaCompileOptions scalaCompileOptions = TestUtil.objectFactory().newInstance(ScalaCompileOptions)
        File scalaLibrary = new File("scala-invalid-2.11.0.jar")
        Set<File> classpath = [scalaLibrary]

        when:
        ScalaCompileOptionsConfigurer.configure(scalaCompileOptions, createToolchain(8), classpath, getVersionParser())

        then:
        !scalaCompileOptions.additionalParameters

        where:
        scalaFileName << [
            "scala-something-else-2.10.0.jar",
            "scala-library.jar",
        ]
    }

    def 'does not configure target jvm if scala compiler already has a target'() {
        given:
        ScalaCompileOptions scalaCompileOptions = TestUtil.objectFactory().newInstance(ScalaCompileOptions)
        scalaCompileOptions.additionalParameters = ['-target:8']
        Set<File> classpath = [new File("scala-library-2.13.1.jar")]

        when:
        ScalaCompileOptionsConfigurer.configure(scalaCompileOptions, createToolchain(8), classpath, getVersionParser())

        then:
        scalaCompileOptions.additionalParameters
        scalaCompileOptions.additionalParameters.find { it == '-target:8' }
        !scalaCompileOptions.additionalParameters.find { it == '-target:17' }
    }

    private static JavaInstallationMetadata createToolchain(Integer javaVersion) {
        new JavaInstallationMetadata() {
            @Override
            JavaLanguageVersion getLanguageVersion() {
                return JavaLanguageVersion.of(javaVersion)
            }

            @Override
            String getJavaRuntimeVersion() { return null }

            @Override
            String getJvmVersion() { return null }

            @Override
            String getVendor() { return null }

            @Override
            Directory getInstallationPath() { return null }
        }
    }
}
