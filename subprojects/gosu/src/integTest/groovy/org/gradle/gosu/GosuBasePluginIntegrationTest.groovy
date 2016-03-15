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

package org.gradle.gosu

import org.gradle.integtests.fixtures.ForkGosuCompileInDaemonModeFixture
import org.gradle.integtests.fixtures.GosuCoverage
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.junit.Rule

import static org.hamcrest.Matchers.startsWith

@TargetCoverage({GosuCoverage.DEFAULT})
class GosuBasePluginIntegrationTest extends MultiVersionIntegrationSpec {

    @Rule public final ForkGosuCompileInDaemonModeFixture forkGosuCompileInDaemonModeFixture = new ForkGosuCompileInDaemonModeFixture(executer, temporaryFolder)

    def "defaults gosuClasspath to inferred Gosu ant tools dependency"() {
        file("build.gradle") << """
        apply plugin: "gosu-base"

        sourceSets {
           custom
        }

        repositories {
           mavenCentral()
        }

        dependencies {
           customCompile "org.gosu-lang.gosu:gosu-ant-tools:$version"
        }

        task gosudoc(type: GosuDoc) {
           classpath = sourceSets.custom.runtimeClasspath
        }

        task verify << {
           assert compileCustomGosu.gosuClasspath.files.any { it.name == "gosu-ant-tools-${version}.jar" }
           assert gosudoc.gosuClasspath.files.any { it.name == "gosu-ant-tools-${version}.jar" }
        }
        """

        expect:
        succeeds("verify")
    }

    def "only resolves source class path feeding into inferred Gosu class path if/when the latter is actually used (but not during autowiring)"() {
        file("build.gradle") << """
apply plugin: "gosu-base"

sourceSets {
    custom
}

repositories {
    mavenCentral()
}

dependencies {
    customCompile "org.gosu-lang.gosu:gosu-core-api:$version"
}

task gosudoc(type: GosuDoc) {
    classpath = sourceSets.custom.runtimeClasspath
}

task verify << {
    assert configurations.customCompile.state.toString() == "UNRESOLVED"
    assert configurations.customRuntime.state.toString() == "UNRESOLVED"
}
        """

        expect:
        succeeds("verify")
    }

    def "not specifying a gosu runtime produces decent error message"() {
        given:
        buildFile << """
            apply plugin: "gosu-base"

            sourceSets {
                main {}
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                compile "com.google.guava:guava:11.0.2"
            }
        """

        file("src/main/gosu/Thing.gs") << """
            class Thing {}
        """

        when:
        fails "compileGosu"

        then:
        failure.assertThatDescription(startsWith("Cannot infer Gosu classpath because the Gosu Core API Jar was not found."))
    }
}
