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

import org.gradle.cache.internal.HeapProportionalCacheSizer
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters
import spock.lang.Issue

@Issue("GRADLE-2460")
class DaemonSystemPropertiesIntegrationTest extends DaemonIntegrationSpec {
    def "standard and sun. client JVM system properties are not carried over to daemon JVM"() {
        given:
        file("build.gradle") << """
task verify {
    doLast {
        assert System.getProperty("java.vendor") != "hollywood"
        assert System.getProperty("java.vendor") != null
        assert System.getProperty("sun.sunny") == null
    }
}
        """

        expect:
        executer.withBuildJvmOpts("-Djava.vendor=hollywood", "-Dsun.sunny=california").withTasks("verify").run()
    }

    def "other client JVM system properties are carried over to daemon JVM"() {
        given:
        file("build.gradle") << """
task verify {
    doLast {
        assert System.getProperty("foo.bar") == "baz"
    }
}
        """

        expect:
        executer.withBuildJvmOpts("-Dfoo.bar=baz").withTasks("verify").run()
    }


    def "forks new daemon when file encoding set to different value via commandline"() {
        setup:
        buildScript """
            task verify {
                doFirst {
                    println "verified = " + java.nio.charset.Charset.defaultCharset().name()
                }
            }
        """

        when:
        executer.withArgument("-Dfile.encoding=UTF-8")
        run("verify")

        then:
        daemons.daemons.size() == 1

        when:
        executer.withArgument("-Dfile.encoding=ISO-8859-1")
        run("verify")

        then:
        output.contains("verified = ISO-8859-1")
        daemons.daemons.size() == 2
    }

    def "forks new daemon when tmpdir is set to different value via commandline"() {
        setup:
        buildScript """
            task verify {
                doFirst {
                    println "verified = \${File.createTempFile('pre', 'post')}"
                }
            }
        """

        def tmpPath1 = tempFolder('folder1')
        when:
        executer.withArgument("-Djava.io.tmpdir=${tmpPath1}")
        run("verify")

        then:
        daemons.daemons.size() == 1
        output.contains("verified = $tmpPath1")

        when:
        def tmpPath2 = tempFolder('tmpPath2')
        executer.withArgument("-Djava.io.tmpdir=${tmpPath2}")
        run "verify"
        then:
        output.contains("verified = $tmpPath2")
        daemons.daemons.size() == 2
    }

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor) // need to start Gradle process from command line to use GRADLE_OPTS
    def "forks new daemon when tmpdir is set to different value via GRADLE_OPTS"() {
        setup:
        buildScript """
            println "GRADLE_VERSION: " + gradle.gradleVersion

            task verify {
                doFirst {
                    println "verified = \${File.createTempFile('pre', 'post')}"
                }
            }
        """

        when:
        executer.withEnvironmentVars(GRADLE_OPTS: "\"-Djava.io.tmpdir=${tempFolder('folder1')}\"")
        run "verify"

        then:
        String gradleVersion = (output =~ /GRADLE_VERSION: (.*)/)[0][1]
        daemons(gradleVersion).daemons.size() == 1

        when:
        def tmpPath2 = tempFolder('tmpPath2')
        executer.withEnvironmentVars(GRADLE_OPTS: "\"-Djava.io.tmpdir=${tmpPath2}\"")
        run "verify"

        then:
        output.contains("verified = $tmpPath2")
        daemons(gradleVersion).daemons.size() == 2
    }

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor) // need to start Gradle process from command line to use GRADLE_OPTS
    def "forks new daemon for changed javax.net.ssl sys properties"() {
        setup:
        buildScript """
            println "GRADLE_VERSION: " + gradle.gradleVersion

            task verify {
                doFirst {
                    println "verified = " + System.getProperty('javax.net.ssl.keyStorePassword')
                }
            }
        """

        when:
        executer.withEnvironmentVars(GRADLE_OPTS: "-Djavax.net.ssl.keyStorePassword=secret");
        run "verify"

        then:
        String gradleVersion = (output =~ /GRADLE_VERSION: (.*)/)[0][1]
        daemons(gradleVersion).daemons.size() == 1
        output.contains("verified = secret")

        when:
        executer.withEnvironmentVars(GRADLE_OPTS: "-Djavax.net.ssl.keyStorePassword=anotherSecret");
        run "verify"

        then:
        output.contains("verified = anotherSecret")
        daemons(gradleVersion).daemons.size() == 2
    }

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor) // need to start Gradle process from command line to use GRADLE_OPTS
    def "forks new daemon for changed cache reserved space sys property"() {
        setup:
        buildScript """
            println "GRADLE_VERSION: " + gradle.gradleVersion

            task verify {
                doFirst {
                    println "verified = " + System.getProperty('${HeapProportionalCacheSizer.CACHE_RESERVED_SYSTEM_PROPERTY}', 'none')
                }
            }
        """

        when:
        run "verify"

        then:
        String gradleVersion = (output =~ /GRADLE_VERSION: (.*)/)[0][1]
        daemons(gradleVersion).daemons.size() == 1
        output.contains("verified = none")

        when:
        executer.withEnvironmentVars(GRADLE_OPTS: "-D${HeapProportionalCacheSizer.CACHE_RESERVED_SYSTEM_PROPERTY}=200");
        run "verify"

        then:
        output.contains("verified = 200")
        daemons(gradleVersion).daemons.size() == 2
    }

    String tempFolder(String folderName) {
        def dir = temporaryFolder.createDir(folderName)
        dir.mkdirs();
        dir.absolutePath
    }

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    def "reuse daemon for changed kotlin dsl cid sys property"() {
        setup:
        buildScript """
            task verify {
                doFirst {
                    println "verified = " + System.getProperty('${KotlinDslModelsParameters.CORRELATION_ID_SYSTEM_PROPERTY_NAME}', '0')
                }
            }
        """

        when:
        executer.withArgument("-Dorg.gradle.kotlin.dsl.provider.cid=24")
        run("verify")

        then:
        daemons.daemons.size() == 1
        output.contains("verified = 24")

        when:
        executer.withArgument("-Dorg.gradle.kotlin.dsl.provider.cid=42")
        run "verify"
        then:
        output.contains("verified = 42")
        daemons.daemons.size() == 1
    }
}
