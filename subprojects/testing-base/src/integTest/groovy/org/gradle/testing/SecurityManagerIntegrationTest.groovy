/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.testing

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.util.GradleVersion
import spock.lang.IgnoreIf

import static org.gradle.util.Matchers.containsText
import static org.gradle.util.Matchers.matchesRegexp

class SecurityManagerIntegrationTest extends AbstractIntegrationSpec {

    @IntegrationTestTimeout(120)
    def "should not hang when running with security manager"() {
        given:
        buildFile << """
apply plugin:"java"

${mavenCentralRepository()}

dependencies {
    testImplementation 'junit:junit:4.13'
}
"""
        file('src/test/java/SecurityManagerTest.java') << '''
import java.security.AccessControlException;

public class SecurityManagerTest {
    @org.junit.Test
    public void testSeqManagerNOTWorking() throws Exception {
        System.setSecurityManager(new SecurityManager());

        try {
            System.setProperty("TestProperty", "value");
        } catch (AccessControlException ex) {
            System.out.println(ex);
        }
        System.setProperty("AnotherProperty", "value");
    }
}
'''

        expect:
        // This test causes the test process to exit ungracefully without closing connections.  This can sometimes
        // cause connection errors to show up in stderr.
        executer.withStackTraceChecksDisabled()
        fails('test')
        failure.assertThatCause(matchesRegexp(".*Process 'Gradle Test Executor \\d+' finished with non-zero exit value 1.*"))
        failure.assertThatCause(containsText("This problem might be caused by incorrect test process configuration."))
        failure.assertThatCause(containsText("Please refer to the test execution section in the User Manual at https://docs.gradle.org/${GradleVersion.current().version}/userguide/java_testing.html#sec:test_execution"))
    }

    @IgnoreIf({ JavaVersion.current() == JavaVersion.VERSION_1_8})
    @IntegrationTestTimeout(120)
    def "should not hang when running with security manager debug flag"() {
        given:
        buildFile << """
apply plugin:"java"

${mavenCentralRepository()}

dependencies {
    testImplementation 'junit:junit:4.13'
}

tasks.named('test').configure {
  systemProperty "java.security.debug", "access,failure"
}
"""
        file("src/test/resources/test.policy") << '''
grant {
  permission java.util.PropertyPermission "*", "read";
  permission java.lang.RuntimePermission "queuePrintJob";

  // to reset at the end of the test
  permission java.lang.RuntimePermission "setSecurityManager";
};
'''
        file('src/test/java/SecurityManagerTest.java') << '''
import java.net.URI;
import java.security.Policy;
import java.security.URIParameter;

public class SecurityManagerTest {

  @org.junit.Test
  public void testSecurityManager() throws Exception {
    URI policyFile = SecurityManagerTest.class.getResource("test.policy").toURI();
    Policy.setPolicy(Policy.getInstance("JavaPolicy",  new URIParameter(policyFile)));

    SecurityManager securityManager = new SecurityManager();
    System.setSecurityManager(securityManager);

    securityManager.checkPermission(new RuntimePermission("queuePrintJob"));
    System.setSecurityManager(null);
  }
}
'''
        expect:
        run('test')
        def result = new JUnitXmlTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('SecurityManagerTest')

        def testClassExecutionResult = result.testClass('SecurityManagerTest')
        testClassExecutionResult.testCount == 1
        testClassExecutionResult.assertStderr(containsNormalizedString('access: access allowed ("java.lang.RuntimePermission" "queuePrintJob")'))
        testClassExecutionResult.assertStderr(containsNormalizedString('access: access allowed ("java.lang.RuntimePermission" "setSecurityManager")'))
    }
}
