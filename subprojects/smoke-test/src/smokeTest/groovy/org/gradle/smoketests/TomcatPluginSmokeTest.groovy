/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.util.GradleVersion
import org.gradle.util.ports.ReleasingPortAllocator
import org.junit.Rule
import spock.lang.Issue

class TomcatPluginSmokeTest extends AbstractSmokeTest {

    @Rule
    final ReleasingPortAllocator portAllocator = new ReleasingPortAllocator()

    @Issue('https://plugins.gradle.org/plugin/com.bmuschko.tomcat')
    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def 'tomcat plugin'() {
        given:
        def httpPort = portAllocator.assignPort()
        def httpsPort = portAllocator.assignPort()
        def stopPort = portAllocator.assignPort()
        buildFile << """
            plugins {
                id "com.bmuschko.tomcat" version "${TestedVersions.tomcat}"
            }

            ${mavenCentralRepository()}

            dependencies {
                def tomcatVersion = '7.0.59'
                tomcat "org.apache.tomcat.embed:tomcat-embed-core:\${tomcatVersion}",
                       "org.apache.tomcat.embed:tomcat-embed-logging-juli:\${tomcatVersion}",
                       "org.apache.tomcat.embed:tomcat-embed-jasper:\${tomcatVersion}"
            }

            ext {
                tomcatStopPort = ${stopPort}
                tomcatStopKey = 'stopKey'
            }

            tomcat {
                httpPort = ${httpPort}
                httpsPort = ${httpsPort}
            }

            task integrationTomcatRun(type: com.bmuschko.gradle.tomcat.tasks.TomcatRun) {
                stopPort = tomcatStopPort
                stopKey = tomcatStopKey
                daemon = true
            }

            task integrationTomcatStop(type: com.bmuschko.gradle.tomcat.tasks.TomcatStop) {
                stopPort = tomcatStopPort
                stopKey = tomcatStopKey
            }

            task integrationTest(type: Test) {
                include '**/*IntegrationTest.*'
                dependsOn integrationTomcatRun
                finalizedBy integrationTomcatStop
            }

            test {
                exclude '**/*IntegrationTest.*'
            }
            """.stripIndent()

        when:
        def result = runner('integrationTest').build()

        then:
        expectDeprecationWarnings(result,
            "Property 'classesJarScanningRequired' is private and annotated with @Internal. " +
                "This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. " +
                "Execution optimizations are disabled due to the failed validation. " +
                "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/more_about_tasks.html#sec:up_to_date_checks for more details.",
            "The AbstractArchiveTask.baseName property has been deprecated. This is scheduled to be removed in Gradle 7.0. Please use the archiveBaseName property instead. " +
                "See https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.tasks.bundling.AbstractArchiveTask.html#org.gradle.api.tasks.bundling.AbstractArchiveTask:baseName for more details."
        )
    }

}
