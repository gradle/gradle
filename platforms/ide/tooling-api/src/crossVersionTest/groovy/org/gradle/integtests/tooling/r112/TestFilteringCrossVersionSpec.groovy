/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.integtests.tooling.r112


import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import spock.lang.Issue

class TestFilteringCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {
    @Issue("GRADLE-2972")
    def "tooling api support test filtering when tasks configured via command line"() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { ${testImplementationConfiguration} 'junit:junit:4.13' }
            compileTestJava.options.fork = true
        """

        file("src/test/java/FooTest.java") << """
            public class FooTest {
                @org.junit.Test public void passes() {}
                @org.junit.Test public void fails() { throw new RuntimeException("Boo!"); }
            }
        """

        when:
        withConnection { it.newBuild().withArguments('test', '--tests', 'FooTest.passes').run() }

        then:
        noExceptionThrown()
    }
}
