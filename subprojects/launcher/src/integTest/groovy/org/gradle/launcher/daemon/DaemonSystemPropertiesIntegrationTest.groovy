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
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Issue
import spock.lang.Unroll

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

    @Unroll
    def "forks new daemon when immutable #propName system property  is set on with different value via commandline"() {
        given:
        buildScript """
            task verify {
                doFirst {
                    println "verified = " + $verifyOutput
                }
            }
        """

        when:
        run "verify", "-D${propName}=${propValue1.call()}"
        then:
        output.contains("verified = " + propValue1.call())
        daemons.daemons.size() == 1

        when:
        run "verify", "-D${propName}=${propValue2.call()}"

        then:
        output.contains("verified = " + propValue2.call())
        daemons.daemons.size() == 2

        where:
        propName         | verifyOutput                                       | propValue1            | propValue2
        "java.io.tmpdir" | "File.createTempFile(\"pre\", \"post\")"           | tempFolder("folder1") | tempFolder("folder2")
        "file.encoding"  | "java.nio.charset.Charset.defaultCharset().name()" | { "UTF-8" }           | { "ISO-8859-1" }
    }

    @Unroll
    def "forks new daemon when immutable system property (#propName) is set on with different value via GRADLE_OPTS"() {
        given:
        executer.requireGradleHome()
        buildScript """
            println "GRADLE_VERSION: " + gradle.gradleVersion

            task verify {
                doFirst {
                    println "verified = " + $verifyOutput
                }
            }
        """

        when:
        executer.withEnvironmentVars(GRADLE_OPTS: "\"-D${propName}=${propValue1.call()}\" -Dorg.gradle.daemon.performance.logging=true");
        run "verify"

        then:
        String gradleVersion = (output =~ /GRADLE_VERSION: (.*)/)[0][1]
        daemons(gradleVersion).daemons.size() == 1

        when:
        executer.withEnvironmentVars(GRADLE_OPTS: "\"-D${propName}=${propValue2.call()}\" -Dorg.gradle.daemon.performance.logging=true");
        run "verify"

        then:
        output.contains("verified = " + propValue2.call())
        daemons(gradleVersion).daemons.size() == 2

        where:
        propName         | verifyOutput                                       | propValue1            | propValue2
        "java.io.tmpdir" | "File.createTempFile(\"pre\", \"post\")"           | tempFolder("folder1") | tempFolder("folder2")
        "file.encoding"  | "java.nio.charset.Charset.defaultCharset().name()" | { "UTF-8" }           | { "ISO-8859-1" }
    }

    Closure tempFolder(String folderName) {
        def dir = new TestNameTestDirectoryProvider().createDir(folderName)
        return { dir.mkdirs(); println "dir "+ dir.absolutePath; dir.absolutePath }
    }
}
