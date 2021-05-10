/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.util.GradleVersion
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class PlayPluginSmokeTest extends AbstractPluginValidatingSmokeTest {

    @Requires(TestPrecondition.JDK11_OR_EARLIER)
    @ToBeFixedForConfigurationCache(because = "unsupported Configuration field")
    def 'build basic Play project'() {
        given:
        useSample("play-example")
        buildFile << """
            plugins {
                id 'org.gradle.playframework' version '${TestedVersions.playframework}'
            }

            repositories {
                ${mavenCentralRepository()}
                ${RepoScriptBlockUtil.lightbendMavenRepositoryDefinition()}
                ${RepoScriptBlockUtil.lightbendIvyRepositoryDefinition()}
            }

            dependencies {
                implementation "com.typesafe.play:play-guice_2.12:2.6.15"
                implementation 'commons-lang:commons-lang:2.6'
                testImplementation "com.google.guava:guava:17.0"
                testImplementation "org.scalatestplus.play:scalatestplus-play_2.12:3.1.2"
                implementation "ch.qos.logback:logback-classic:1.2.3"
            }
        """

        when:
        def result = runner('build')
            .expectDeprecationWarning(
                "The CreateStartScripts.mainClassName property has been deprecated. " +
                    "This is scheduled to be removed in Gradle 8.0. Please use the mainClass property instead. " +
                    "See https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.jvm.application.tasks.CreateStartScripts.html#org.gradle.jvm.application.tasks.CreateStartScripts:mainClassName for more details.",
                "https://github.com/gradle/playframework/pull/168")
            .expectDeprecationWarning(
                "The WorkerExecutor.submit() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 8.0. Please use the noIsolation(), classLoaderIsolation() or processIsolation() method instead. " +
                    "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_5.html#method_workerexecutor_submit_is_deprecated for more details.",
                "https://github.com/gradle/playframework/pull/167")
            .build()

        then:
        result.task(':build').outcome == SUCCESS
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'org.gradle.playframework': Versions.of(TestedVersions.playframework)
        ]
    }
}
