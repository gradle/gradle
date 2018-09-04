/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.tooling.r50


import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.build.BuildEnvironment

@ToolingApiVersion('>=5.0')
class ArgumentPassingCrossVersionTest extends ToolingApiSpecification {

    static final String JVM_ARG_1 = '-verbosegc'
    static final String JVM_ARG_2 = '-XX:+PrintGCDetails'
    static final String ARG_1 = "argument1"
    static final String ARG_2 = "argument2"

    def setup() {
        buildFile << """
            if(hasProperty('$ARG_1')) logger.quiet("$ARG_1")
            if(hasProperty('$ARG_2')) logger.quiet("$ARG_2")
        """
    }

    def "Appends additional JVM arguments"() {
        when:
        BuildEnvironment env
        withConnection {
            env = it.model(BuildEnvironment.class).addJvmArguments(JVM_ARG_1).get()
        }

        then:
        env.java.jvmArguments.contains(JVM_ARG_1)
    }

    def "Appends additional JVM arguments multiple times"() {
        when:
        BuildEnvironment env
        withConnection {
            env = it.model(BuildEnvironment.class).addJvmArguments(JVM_ARG_1).addJvmArguments(JVM_ARG_2).get()
        }

        then:
        env.java.jvmArguments.contains(JVM_ARG_1)
        env.java.jvmArguments.contains(JVM_ARG_2)
    }

    def "Adds multiple JVM arguments at once"() {
        when:
        BuildEnvironment env
        withConnection {
            env = it.model(BuildEnvironment.class).addJvmArguments(JVM_ARG_1, JVM_ARG_2).get()
        }

        then:
        env.java.jvmArguments.contains(JVM_ARG_1)
        env.java.jvmArguments.contains(JVM_ARG_2)
    }

    def "Adding JVM argument does not overwrite existing values"() {
        when:
        BuildEnvironment env
        withConnection {
            env = it.model(BuildEnvironment.class).setJvmArguments(JVM_ARG_1).addJvmArguments(JVM_ARG_2).get()
        }

        then:
        env.java.jvmArguments.contains(JVM_ARG_1)
        env.java.jvmArguments.contains(JVM_ARG_2)
    }

    def "Appends additional arguments"() {
        when:
        ByteArrayOutputStream output = new ByteArrayOutputStream()
        withConnection {
            it.newBuild().setStandardOutput(output).addArguments("-P$ARG_1").run()
        }

        then:
        output.toString().contains(ARG_1)
    }

    def "Appends arguments multiple times"() {
        when:
        ByteArrayOutputStream output = new ByteArrayOutputStream()
        withConnection {
            it.newBuild().setStandardOutput(output).addArguments("-P$ARG_1").addArguments("-P$ARG_2").run()
        }

        then:
        output.toString().contains(ARG_1)
        output.toString().contains(ARG_2)
    }

    def "Adds multiple arguments at once"() {
        when:
        ByteArrayOutputStream output = new ByteArrayOutputStream()
        withConnection {
            it.newBuild().setStandardOutput(output).addArguments("-P$ARG_1", "-P$ARG_2").run()
        }

        then:
        output.toString().contains(ARG_1)
        output.toString().contains(ARG_2)
    }

    def "Adding argument does not overwrite existing values"() {
        when:
        ByteArrayOutputStream output = new ByteArrayOutputStream()
        withConnection {
            it.newBuild().setStandardOutput(output).withArguments("-P$ARG_1").addArguments("-P$ARG_2").run()
        }

        then:
        output.toString().contains(ARG_1)
        output.toString().contains(ARG_2)
    }
}
