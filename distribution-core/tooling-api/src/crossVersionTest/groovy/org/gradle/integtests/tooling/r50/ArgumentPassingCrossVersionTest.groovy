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

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildLauncher
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
        BuildEnvironment env1 = loadBuildEnvironment { builder -> builder.addJvmArguments(JVM_ARG_1) }

        then:
        env1.java.jvmArguments.contains(JVM_ARG_1)

        when:
        BuildEnvironment env2 = loadBuildEnvironment { builder -> builder.addJvmArguments([JVM_ARG_1]) }

        then:
        env2.java.jvmArguments.contains(JVM_ARG_1)
    }

    def "Appends additional JVM arguments multiple times"() {
        when:
        BuildEnvironment env1 = loadBuildEnvironment { builder -> builder.addJvmArguments(JVM_ARG_1).addJvmArguments(JVM_ARG_2) }

        then:
        env1.java.jvmArguments.contains(JVM_ARG_1)
        env1.java.jvmArguments.contains(JVM_ARG_2)

        when:
        BuildEnvironment env2 = loadBuildEnvironment { builder -> builder.addJvmArguments([JVM_ARG_1]).addJvmArguments([JVM_ARG_2]) }

        then:
        env2.java.jvmArguments.contains(JVM_ARG_1)
        env2.java.jvmArguments.contains(JVM_ARG_2)
    }

    def "Adds multiple JVM arguments at once"() {
        when:
        BuildEnvironment env1 = loadBuildEnvironment { builder -> builder.addJvmArguments(JVM_ARG_1, JVM_ARG_2) }

        then:
        env1.java.jvmArguments.contains(JVM_ARG_1)
        env1.java.jvmArguments.contains(JVM_ARG_2)

        when:
        BuildEnvironment env2 = loadBuildEnvironment { builder -> builder.addJvmArguments([JVM_ARG_1, JVM_ARG_2]) }

        then:
        env2.java.jvmArguments.contains(JVM_ARG_1)
        env2.java.jvmArguments.contains(JVM_ARG_2)
    }

    def "Adding JVM argument does not overwrite existing values"() {
        when:
        BuildEnvironment env1 = loadBuildEnvironment { builder -> builder.setJvmArguments(JVM_ARG_1).addJvmArguments(JVM_ARG_2) }

        then:
        env1.java.jvmArguments.contains(JVM_ARG_1)
        env1.java.jvmArguments.contains(JVM_ARG_2)

        when:
        BuildEnvironment env2 = loadBuildEnvironment { builder -> builder.setJvmArguments(JVM_ARG_1).addJvmArguments([JVM_ARG_2]) }

        then:
        env2.java.jvmArguments.contains(JVM_ARG_1)
        env2.java.jvmArguments.contains(JVM_ARG_2)
    }

    def "Adding zero JVM arguments is a no-op"() {
        expect:
        loadBuildEnvironment { builder -> builder.addJvmArguments() }
    }

    def "Adding null JVM argument throws NPE"() {
        when:
        loadBuildEnvironment { builder -> builder.addJvmArguments(null as String) }

        then:
        thrown(NullPointerException)

        when:
        loadBuildEnvironment { builder -> builder.addJvmArguments(null as List) }

        then:
        thrown(NullPointerException)
    }

    def "Appends additional arguments"() {
        when:
        String output1 = runBuild { launcher -> launcher.addArguments("-P$ARG_1") }

        then:
        output1.contains(ARG_1)

        when:
        String output2 = runBuild { launcher -> launcher.addArguments(["-P$ARG_1" as String]) }

        then:
        output2.contains(ARG_1)
    }

    def "Appends arguments multiple times"() {
        when:
        String output1 = runBuild { launcher -> launcher.addArguments("-P$ARG_1").addArguments("-P$ARG_2") }

        then:
        output1.toString().contains(ARG_1)
        output1.toString().contains(ARG_2)

        when:
        String output2 = runBuild { launcher -> launcher.addArguments(["-P$ARG_1" as String]).addArguments(["-P$ARG_2" as String]) }

        then:
        output2.toString().contains(ARG_1)
        output2.toString().contains(ARG_2)
    }

    def "Adds multiple arguments at once"() {
        when:
        String output1 = runBuild { launcher -> launcher.addArguments("-P$ARG_1", "-P$ARG_2") }

        then:
        output1.toString().contains(ARG_1)
        output1.toString().contains(ARG_2)

        when:
        String output2 = runBuild { launcher -> launcher.addArguments(["-P$ARG_1" as String, "-P$ARG_2" as String]) }

        then:
        output2.toString().contains(ARG_1)
        output2.toString().contains(ARG_2)
    }

    def "Adding argument does not overwrite existing values"() {
        when:
        String output1 = runBuild { launcher -> launcher.withArguments("-P$ARG_1").addArguments("-P$ARG_2") }

        then:
        output1.toString().contains(ARG_1)
        output1.toString().contains(ARG_2)

        when:
        String output2 = runBuild { launcher -> launcher.withArguments("-P$ARG_1").addArguments(["-P$ARG_2" as String]) }

        then:
        output2.toString().contains(ARG_1)
        output2.toString().contains(ARG_2)
    }

    def "Adding zero arguments is a no-op"() {
        expect:
        runBuild { launcher -> launcher.addArguments() }
    }

    def "Adding null argument throws NPE"() {
        when:
        runBuild { launcher -> launcher.addArguments(null as String) }

        then:
        thrown(NullPointerException)

        when:
        runBuild { launcher -> launcher.addArguments(null as List) }

        then:
        thrown(NullPointerException)
    }

    private BuildEnvironment loadBuildEnvironment(@ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ModelBuilder"]) Closure config) {
        BuildEnvironment env
        withConnection {
            def builder = it.model(BuildEnvironment)
            config(builder)
            env = builder.get()
        }
        env
    }

    private String runBuild(@ClosureParams(value = SimpleType, options = ["org.gradle.tooling.BuildLauncher"]) Closure config) {
        ByteArrayOutputStream output = new ByteArrayOutputStream()
        withConnection {
            BuildLauncher launcher = it.newBuild().setStandardOutput(output)
            config(launcher)
            launcher.run()
        }
        return output.toString()
    }
}
