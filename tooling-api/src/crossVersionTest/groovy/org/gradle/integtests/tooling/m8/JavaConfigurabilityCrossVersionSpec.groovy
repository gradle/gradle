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


import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.util.GradleVersion
import spock.lang.Issue
import spock.lang.Timeout

class JavaConfigurabilityCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        //this test does not make any sense in embedded mode
        //as we don't own the process
        toolingApi.requireDaemons()
    }

    def "configures the java settings"() {
        when:
        BuildEnvironment env = withConnection {
            def model = it.model(BuildEnvironment.class)
            model
                .setJvmArguments("-Xmx333m", "-Xms13m")
                .get()
        }

        then:
        env.java.javaHome
        env.java.jvmArguments.contains "-Xms13m"
        env.java.jvmArguments.contains "-Xmx333m"
    }

    def "uses sensible java defaults if nulls configured"() {
        when:
        BuildEnvironment env = withConnection {
            def model = it.model(BuildEnvironment.class)
            model
                .setJvmArguments(null)
                .get()
        }

        then:
        env.java.javaHome
        if (targetVersion < GradleVersion.version("5.0")) {
            env.java.jvmArguments.contains("-Xmx1024m")
        } else {
            env.java.jvmArguments.contains("-Xmx512m")
        }
        env.java.jvmArguments.contains("-XX:+HeapDumpOnOutOfMemoryError")
    }

    def "tooling api provided jvm args take precedence over gradle.properties"() {
        file('build.gradle') << """
assert java.lang.management.ManagementFactory.runtimeMXBean.inputArguments.contains('-Xmx53m')
assert System.getProperty('some-prop') == 'BBB'
"""
        file('gradle.properties') << "org.gradle.jvmargs=-Dsome-prop=AAA -Xmx16m"

        when:
        def model = withConnection {
            it.model(GradleProject.class)
                .setJvmArguments('-Dsome-prop=BBB', '-Xmx53m')
                .get()
        }

        then:
        model != null
    }

    def "customized java args are reflected in the inputArguments and the build model"() {
        given:
        file('build.gradle') <<
                "project.description = java.lang.management.ManagementFactory.runtimeMXBean.inputArguments.join('##')"

        when:
        BuildEnvironment env
        GradleProject project
        withConnection {
            env = it.model(BuildEnvironment.class).setJvmArguments('-Xmx200m', '-Xms100m').get()
            project = it.model(GradleProject.class).setJvmArguments('-Xmx200m', '-Xms100m').get()
        }

        then:
        def inputArgsInBuild = project.description.split('##') as List
        env.java.jvmArguments.each { inputArgsInBuild.contains(it) }
    }

    @Issue("GRADLE-1799")
    @Timeout(25)
    def "promptly discovers when java does not exist"() {
        when:
        withConnection {
            it.newBuild().setJavaHome(new File("i dont exist"))
        }

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("i dont exist")
    }
}
