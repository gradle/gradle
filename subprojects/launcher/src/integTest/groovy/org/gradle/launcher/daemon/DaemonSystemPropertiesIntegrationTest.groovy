/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.daemon

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import spock.lang.Issue

import java.nio.charset.Charset

@Issue("GRADLE-2460")
class DaemonSystemPropertiesIntegrationTest extends DaemonIntegrationSpec {
    def "standard and sun. client JVM system properties are not carried over to daemon JVM"() {
        given:
        file("build.gradle") << """
task verify << {
    assert System.getProperty("java.vendor") != "hollywood"
    assert System.getProperty("java.vendor") != null
    assert System.getProperty("sun.sunny") == null
}
        """

        expect:
        executer.withBuildJvmOpts("-Djava.vendor=hollywood", "-Dsun.sunny=california").withTasks("verify").run()
    }

    def "other client JVM system properties are carried over to daemon JVM"() {
        given:
        file("build.gradle") << """
task verify << {
    assert System.getProperty("foo.bar") == "baz"
}
        """

        expect:
        executer.withBuildJvmOpts("-Dfoo.bar=baz").withTasks("verify").run()

    }

    def "forks new daemon when immutable system property is set on with different value via commandline"() {
        given:
        def encoding = Charset.defaultCharset().name()
        assert encoding != "ISO-8859-1"

        buildScript """
            task encoding {
                doFirst { println "encoding = " + java.nio.charset.Charset.defaultCharset().name() }
            }
        """

        when:
        run "encoding", "-Dfile.encoding=$encoding"
        then:
        output.contains("encoding = $encoding")
        daemons.daemons.size() == 1


        when:
        run "encoding", "-Dfile.encoding=ISO-8859-1"

        then:
        output.contains("encoding = ISO-8859-1")
        daemons.daemons.size() == 2
    }

    def "forks new daemon when immutable system property is set on with different value via GRADLE_OPTS"() {
        given:
        executer.requireGradleHome()
        def encoding = Charset.defaultCharset().name()
        assert encoding != "ISO-8859-1"

        buildScript """
            task encoding {
                doFirst {
                    println "GRADLE_VERSION: " + gradle.gradleVersion
                    println "encoding = " + java.nio.charset.Charset.defaultCharset().name()
                }
            }
        """

        when:
        executer.withEnvironmentVars(GRADLE_OPTS:"-Dfile.encoding=$encoding")
        run "encoding"

        then:
        String gradleVersion = (output =~ /GRADLE_VERSION: (.*)/)[0][1]
        output.contains("encoding = $encoding")
        daemons(gradleVersion).daemons.size() == 1

        when:
        executer.withEnvironmentVars(GRADLE_OPTS:"-Dfile.encoding=ISO-8859-1")
        run "encoding"

        then:
        output.contains("encoding = ISO-8859-1")
        daemons(gradleVersion).daemons.size() == 2
    }
}
