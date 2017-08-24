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

import org.gradle.initialization.BuildLayoutParameters
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.initialization.option.GradleBuildOptions.DAEMON_IDLE_TIMEOUT
import static org.gradle.initialization.option.GradleBuildOptions.JVM_ARGS

class LayoutToPropertiesConverterTest extends Specification {

    @Rule SetSystemProperties sysProperties = new SetSystemProperties()
    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()
    def converter = new LayoutToPropertiesConverter()
    BuildLayoutParameters layout
    Map<String, String> props = new HashMap<String, String>()

    def setup() {
        layout = new BuildLayoutParameters()
            .setGradleUserHomeDir(temp.createDir("gradleHome"))
            .setProjectDir(temp.createDir("projectDir"))
            .setSearchUpwards(false)
    }

    def "only configures gradle properties"() {
        when:
        temp.file("gradleHome/gradle.properties") << "foo=bar"

        then:
        converter.convert(layout, props).foo == null
    }

    def "configures from gradle home dir"() {
        when:
        temp.file("gradleHome/gradle.properties") << "$JVM_ARGS.gradleProperty=-Xmx1024m -Dprop=value"

        then:
        converter.convert(layout, props).get(JVM_ARGS.gradleProperty) == '-Xmx1024m -Dprop=value'
    }

    def "configures from project dir"() {
        when:
        temp.file("projectDir/gradle.properties") << "$DAEMON_IDLE_TIMEOUT.gradleProperty=125"

        then:
        converter.convert(layout, props).get(DAEMON_IDLE_TIMEOUT.gradleProperty) == "125"
    }

    def "configures from root dir in a multiproject build"() {
        when:
        temp.file("projectDir/settings.gradle") << "include 'foo'"
        temp.file("projectDir/gradle.properties") << "$JVM_ARGS.gradleProperty=-Xmx128m"
        layout.setProjectDir(temp.file("projectDir/foo"))
        layout.searchUpwards = true

        then:
        converter.convert(layout, props).get(JVM_ARGS.gradleProperty) == '-Xmx128m'
    }

    def "gradle home properties take precedence over project dir properties"() {
        when:
        temp.file("gradleHome/gradle.properties") << "$JVM_ARGS.gradleProperty=-Xmx1024m"
        temp.file("projectDir/gradle.properties") << "$JVM_ARGS.gradleProperty=-Xmx512m"

        then:
        converter.convert(layout, props).get(JVM_ARGS.gradleProperty) == '-Xmx1024m'
    }

    def "system property takes precedence over gradle home"() {
        when:
        temp.file("gradleHome/gradle.properties") << "$JVM_ARGS.gradleProperty=-Xmx1024m"
        System.setProperty(JVM_ARGS.gradleProperty, '-Xmx2048m')

        then:
        converter.convert(layout, props).get(JVM_ARGS.gradleProperty) == '-Xmx2048m'
    }

    def "non-serializable system properties are ignored"() {
        when:
        System.getProperties().put('foo', NULL_OBJECT)

        then:
        converter.convert(layout, props).foo == null
    }

    static class NotSerializable {
    }
    static final NULL_OBJECT = new NotSerializable()
}
