/*
 * Copyright 2013 the original author or authors.
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


package org.gradle.launcher

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf

/**
 * by Szczepan Faber, created at: 11/21/12
 */
@IgnoreIf({ GradleContextualExecuter.parallel })
class EnablingParallelExecutionIntegrationTest extends AbstractIntegrationSpec {

    void setup() {
        executer.beforeExecute { it.withArguments("-u") } //to avoid using unwanted gradle.properties
    }

    def "parallel mode enabled from command line"() {
        buildFile << "assert gradle.startParameter.parallelThreadCount == 15"
        expect:
        run("--parallel-threads=15")
    }

    def "parallel mode enabled via gradle.properties"() {
        file("gradle.properties") << "org.gradle.parallel=true"
        buildFile << "assert gradle.startParameter.parallelThreadCount == -1"
        expect:
        run()
    }

    def "parallel mode setting at command line takes precedence over gradle.properties"() {
        file("gradle.properties") << "org.gradle.parallel=false"
        buildFile << "assert gradle.startParameter.parallelThreadCount == 15"
        expect:
        run("--parallel-threads=15")
    }
}