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

package org.gradle.launcher.cli.converter

import org.gradle.cli.CommandLineParser
import org.gradle.initialization.BuildLayoutParameters
import org.gradle.initialization.layout.BuildLayoutFactory
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

class LayoutToPropertiesConverterTest extends Specification {
    @Rule
    SetSystemProperties sysProperties = new SetSystemProperties()
    @Rule
    TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider(getClass())
    def converter = new LayoutToPropertiesConverter(new BuildLayoutFactory())
    def gradleDistribution = temp.createDir("gradleDistribution")
    def gradleHome = temp.createDir("gradleHome")
    def rootDir = temp.createDir("projectDir")

    def "only extracts properties for known build options"() {
        when:
        def layout = layout()
        gradleHome.file("gradle.properties") << "foo=bar"

        then:
        converter.convert(layout).properties.foo == null
    }

    def "configures from installation home dir"() {
        when:
        def layout = layout()
        gradleDistribution.file("gradle.properties") << "$DaemonBuildOptions.JvmArgsOption.GRADLE_PROPERTY=-Xmx1024m -Dprop=value"

        then:
        converter.convert(layout).properties.get(DaemonBuildOptions.JvmArgsOption.GRADLE_PROPERTY) == '-Xmx1024m -Dprop=value'
    }

    def "configures from user home dir"() {
        when:
        def layout = layout()
        gradleHome.file("gradle.properties") << "$DaemonBuildOptions.JvmArgsOption.GRADLE_PROPERTY=-Xmx1024m -Dprop=value"

        then:
        converter.convert(layout).properties.get(DaemonBuildOptions.JvmArgsOption.GRADLE_PROPERTY) == '-Xmx1024m -Dprop=value'
    }

    def "configures from build root dir"() {
        when:
        def layout = layout()
        rootDir.file("gradle.properties") << "$DaemonBuildOptions.IdleTimeoutOption.GRADLE_PROPERTY=125"

        then:
        converter.convert(layout).properties.get(DaemonBuildOptions.IdleTimeoutOption.GRADLE_PROPERTY) == "125"
    }

    def "configures from -D command line argument"() {
        when:
        def layout = layout("-D$DaemonBuildOptions.IdleTimeoutOption.GRADLE_PROPERTY=125")

        then:
        converter.convert(layout).properties.get(DaemonBuildOptions.IdleTimeoutOption.GRADLE_PROPERTY) == "125"
    }

    def "configures from system property of current JVM"() {
        when:
        def layout = layout()
        System.setProperty(DaemonBuildOptions.IdleTimeoutOption.GRADLE_PROPERTY, "125")

        then:
        converter.convert(layout).properties.get(DaemonBuildOptions.IdleTimeoutOption.GRADLE_PROPERTY) == "125"
    }

    def "configures from root dir in a multiproject build"() {
        when:
        def layout = layout {
            setProjectDir(rootDir.file("foo"))
            searchUpwards = true
        }
        rootDir.file("settings.gradle") << "include 'foo'"
        rootDir.file("gradle.properties") << "$DaemonBuildOptions.JvmArgsOption.GRADLE_PROPERTY=-Xmx128m"

        then:
        converter.convert(layout).properties.get(DaemonBuildOptions.JvmArgsOption.GRADLE_PROPERTY) == '-Xmx128m'
    }

    def "root dir properties take precedence over gradle installation home properties"() {
        when:
        def layout = layout()
        rootDir.file("gradle.properties") << "$DaemonBuildOptions.JvmArgsOption.GRADLE_PROPERTY=-Xmx1024m"
        gradleDistribution.file("gradle.properties") << "$DaemonBuildOptions.JvmArgsOption.GRADLE_PROPERTY=-Xmx512m"

        then:
        converter.convert(layout).properties.get(DaemonBuildOptions.JvmArgsOption.GRADLE_PROPERTY) == '-Xmx1024m'
    }

    def "gradle home properties take precedence over root dir properties"() {
        when:
        def layout = layout()
        gradleHome.file("gradle.properties") << "$DaemonBuildOptions.JvmArgsOption.GRADLE_PROPERTY=-Xmx1024m"
        rootDir.file("gradle.properties") << "$DaemonBuildOptions.JvmArgsOption.GRADLE_PROPERTY=-Xmx512m"

        then:
        converter.convert(layout).properties.get(DaemonBuildOptions.JvmArgsOption.GRADLE_PROPERTY) == '-Xmx1024m'
    }

    def "system property of current JVM takes precedence over gradle home"() {
        when:
        def layout = layout()
        gradleHome.file("gradle.properties") << "$DaemonBuildOptions.JvmArgsOption.GRADLE_PROPERTY=-Xmx1024m"
        System.setProperty(DaemonBuildOptions.JvmArgsOption.GRADLE_PROPERTY, '-Xmx2048m')

        then:
        converter.convert(layout).properties.get(DaemonBuildOptions.JvmArgsOption.GRADLE_PROPERTY) == '-Xmx2048m'
    }

    def "-D command-line option takes precedence over system property of current JVM"() {
        when:
        def layout = layout("-D$DaemonBuildOptions.JvmArgsOption.GRADLE_PROPERTY=-Xmx2048m")
        System.setProperty(DaemonBuildOptions.JvmArgsOption.GRADLE_PROPERTY, '-Xmx512m')

        then:
        converter.convert(layout).properties.get(DaemonBuildOptions.JvmArgsOption.GRADLE_PROPERTY) == '-Xmx2048m'
    }

    def "non-serializable system properties are ignored"() {
        when:
        def layout = layout()
        System.getProperties().put('foo', NULL_OBJECT)

        then:
        converter.convert(layout).properties.foo == null
    }

    BuildLayoutConverter.Result layout(@DelegatesTo(BuildLayoutParameters) Closure overrides = {}, String... args) {
        def parser = new CommandLineParser()
        def converter = new BuildLayoutConverter()
        converter.configure(parser)
        return converter.convert(parser.parse(args)) {
            it.setGradleInstallationHomeDir(gradleDistribution)
            it.setGradleUserHomeDir(gradleHome)
            it.setProjectDir(rootDir)
            it.setSearchUpwards(false)
            overrides.delegate = it
            overrides()
        }
    }

    static class NotSerializable {
    }
    static final NULL_OBJECT = new NotSerializable()
}
