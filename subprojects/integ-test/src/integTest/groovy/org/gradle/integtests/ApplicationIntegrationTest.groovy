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
package org.gradle.integtests

import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.GradleExecuter
import org.gradle.integtests.fixtures.ScriptExecuter
import org.junit.Rule
import spock.lang.Specification

class ApplicationIntegrationTest extends Specification {
    @Rule public final GradleDistribution distribution = new GradleDistribution()
    @Rule public final GradleExecuter executer = new GradleDistributionExecuter()

    def canUseEnvironmentVariableToPassOptionsToJvmWhenRunningScript() {
        distribution.testFile('settings.gradle') << 'rootProject.name = "application"'
        distribution.testFile('build.gradle') << '''
apply plugin: 'application'
mainClassName = 'org.gradle.test.Main'
'''
        distribution.testFile('src/main/java/org/gradle/test/Main.java') << '''
package org.gradle.test;

class Main {
    public static void main(String[] args) {
        if (System.getProperty("testValue") == null) {
            throw new RuntimeException("Expected system property not specified");
        }
    }
}
'''

        when:
        executer.withTasks('install').run()
        
        def builder = new ScriptExecuter()
        builder.workingDir distribution.testDir.file('build/install/application/bin')
        builder.executable "application"
        builder.environment('APPLICATION_OPTS', '-DtestValue=value')

        def result = builder.run()

        then:
        result.assertNormalExitValue()
    }
}
