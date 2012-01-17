/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.integtests.tooling.m8

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.tooling.fixture.MinTargetGradleVersion
import org.gradle.integtests.tooling.fixture.MinToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.Project
import org.gradle.tooling.model.build.BuildEnvironment
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Issue

@MinToolingApiVersion('1.0-milestone-8')
@MinTargetGradleVersion('1.0-milestone-8')
class JavaConfigurabilityIntegrationTest extends ToolingApiSpecification {

    def setup() {
        //this test does not make any sense in embedded mode
        //as we don't own the process
        toolingApi.isEmbedded = false
    }

    def "configures the java settings"() {
        when:
        BuildEnvironment env = withConnection {
            def model = it.model(BuildEnvironment.class)
            model
                .setJavaHome(new File("hey"))
                .setJvmArguments("-Xmx333m", "-Xms13m")
                .get()
        }

        then:
        env.java.javaHome == new File("hey")
        env.java.jvmArguments.contains("-Xmx333m")
        env.java.jvmArguments.contains("-Xms13m")
    }

    def "uses sensible java defaults if nulls configured"() {
        when:
        BuildEnvironment env = withConnection {
            def model = it.model(BuildEnvironment.class)
            model
                .setJavaHome(null)
                .setJvmArguments(null)
                .get()
        }

        then:
        env.java.javaHome
        !env.java.jvmArguments.empty
    }

    def "may use no jvm args if requested"() {
        when:
        BuildEnvironment env = withConnection {
            def model = it.model(BuildEnvironment.class)
            model
                .setJvmArguments(new String[0])
                .get()
        }

        then:
        env.java.jvmArguments == []
    }

    def "tooling api provided jvm args take precedence over gradle.properties"() {
        dist.file('build.gradle') << """
assert java.lang.management.ManagementFactory.runtimeMXBean.inputArguments.contains('-Xmx23m')
assert System.getProperty('some-prop') == 'BBB'
"""
        dist.file('gradle.properties') << "org.gradle.jvmargs=-Dsome-prop=AAA -Xmx16m"
        //TODO SF should we combine the different properties?

        when:
        def model = withConnection {
            it.model(Project.class)
                .setJvmArguments('-Dsome-prop=BBB', '-Xmx23m')
                .get()
        }

        then:
        model != null
    }

    def "customized java args are reflected in the inputArguments and the build model"() {
        given:
        dist.file('build.gradle') <<
                "project.description = java.lang.management.ManagementFactory.runtimeMXBean.inputArguments.join('##')"

        when:
        BuildEnvironment env
        Project project
        withConnection {
            env = it.model(BuildEnvironment.class).setJvmArguments('-Xmx200m', '-Xms100m').get()
            project = it.model(Project.class).setJvmArguments('-Xmx200m', '-Xms100m').get()
        }

        then:
        def inputArgsInBuild = project.description.split('##')
        inputArgsInBuild.length == env.java.jvmArguments.size()
        inputArgsInBuild.each { env.java.jvmArguments.contains(it) }
    }

    @IgnoreIf({ AvailableJavaHomes.bestAlternative == null })
    def "customized java home is reflected in the java.home and the build model"() {
        given:
        dist.file('build.gradle') << "project.description = System.getProperty('java.home')"

        when:
        File javaHome = AvailableJavaHomes.bestAlternative
        BuildEnvironment env
        Project project
        withConnection {
            env = it.model(BuildEnvironment.class).setJavaHome(javaHome).get()
            project = it.model(Project.class).setJavaHome(javaHome).get()
        }

        then:
        project.description.startsWith(env.java.javaHome.toString())
    }

    @IgnoreIf({ AvailableJavaHomes.bestAlternative == null })
    def "tooling api provided java home takes precedence over gradle.properties"() {
        File javaHome = AvailableJavaHomes.bestAlternative
        File dummyJavaHome = dist.file("dummyJavaHome").createDir()

        dist.file('build.gradle') << "assert System.getProperty('java.home').startsWith('$javaHome')"
        dist.file('gradle.properties') << "org.gradle.java.home=${dummyJavaHome.absolutePath}"

        when:
        def env = withConnection {
            it.newBuild().setJavaHome(javaHome).run() //the assert
            it.model(BuildEnvironment.class)
                    .setJavaHome(javaHome)
                    .get()
        }

        then:
        env != null
        env.java.javaHome == javaHome
    }

    @Ignore
    @Issue("GRADLE-1799")
    def "behaves reasonably when rubbish java home"() {
        when:
        withConnection {
            it.newBuild()
                .setJavaHome(new File("hey"))
                .run()
        }

        then: "behaves sanely, and the exception contains useful info"
    }

    @Ignore
    @Issue("GRADLE-1799")
    def "behaves reasonably when rubbish jvm arguments supplied"() {
        when:
        withConnection {
            it.newBuild()
                .setJvmArguments("-Xasdf")
                .run()
        }

        then: "behaves sanely, and the exception contains useful info"
    }
}
