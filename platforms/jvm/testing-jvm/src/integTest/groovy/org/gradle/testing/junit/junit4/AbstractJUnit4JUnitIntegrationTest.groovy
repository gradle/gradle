/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.testing.junit.junit4

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.testing.junit.AbstractJUnitIntegrationTest
import org.junit.Assume
import spock.lang.Issue

abstract class AbstractJUnit4JUnitIntegrationTest extends AbstractJUnitIntegrationTest implements JUnit4CommonTestSources {
    abstract boolean isSupportsBlockJUnit4ClassRunner()

    @Issue("https://issues.gradle.org//browse/GRADLE-3114")
    def "creates runner before tests"() {
        Assume.assumeTrue(supportsBlockJUnit4ClassRunner)

        given:
        file('src/test/java/org/gradle/CustomRunner.java') << """
            package org.gradle;

            import java.lang.reflect.Method;
            import org.junit.runner.notification.RunNotifier;
            import org.junit.runners.BlockJUnit4ClassRunner;
            import org.junit.runners.model.FrameworkMethod;
            import org.junit.runners.model.InitializationError;
            import org.junit.runners.model.Statement;

            public class CustomRunner extends BlockJUnit4ClassRunner {
                public static boolean isClassUnderTestLoaded;
                private final Class<?> bootstrappedTestClass;

                public CustomRunner(Class<?> clazz) throws Exception {
                    super(clazz);
                    bootstrappedTestClass = clazz;
                }

                @Override
                protected Statement methodBlock(final FrameworkMethod method) {
                    return new Statement() {
                        @Override
                        public void evaluate() throws Throwable {

                            if(isClassUnderTestLoaded){
                                throw new RuntimeException("Test Class should not be loaded");
                            }

                            final HelperTestRunner helperTestRunner = new HelperTestRunner(bootstrappedTestClass);
                            final Method bootstrappedMethod = bootstrappedTestClass.getMethod(method.getName());
                            final Statement statement = helperTestRunner.methodBlock(new FrameworkMethod(bootstrappedMethod));
                            statement.evaluate();
                        }
                    };
                }

                public class HelperTestRunner extends BlockJUnit4ClassRunner {
                    public HelperTestRunner(Class<?> testClass) throws InitializationError {
                        super(testClass);
                    }

                    @Override
                    protected Object createTest() throws Exception {
                        return super.createTest();
                    }

                    @Override
                    public Statement classBlock(RunNotifier notifier) {
                        return super.classBlock(notifier);
                    }

                    @Override
                    public Statement methodBlock(FrameworkMethod method) {
                        return super.methodBlock(method);
                    }
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/ExecutionOrderTest.java') << """
            package org.gradle;

            ${testFrameworkImports}

            ${getRunOrExtendWithAnnotation('CustomRunner.class')}
            public class ExecutionOrderTest {

                static{
                    CustomRunner.isClassUnderTestLoaded = true;
                }

                @Test
                public void classUnderTestIsLoadedOnlyByRunner(){
                    // The CustomRunner class will fail this test if this class is initialized before its
                    // run method is triggered.
                }
            }
        """.stripIndent()
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test {
                ${configureTestFramework}
            }
        """.stripIndent()

        when:
        executer.withTasks('test').run()

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.ExecutionOrderTest')
        result.testClass('org.gradle.ExecutionOrderTest').assertTestPassed('classUnderTestIsLoadedOnlyByRunner')
    }
}
