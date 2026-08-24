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
package org.gradle.testretry

import groovy.transform.NamedVariant

abstract class AbstractGeneralPluginFuncTest extends AbstractPluginFuncTest {

    String getLanguagePlugin() {
        return 'java'
    }

    protected void successfulTest() {
        writeJavaTestSource """
            package acme;

            public class SuccessfulTests {
                @org.junit.Test
                public void successTest() {}
            }
        """
    }

    protected void failedTest() {
        writeJavaTestSource """
            package acme;

            import static org.junit.Assert.assertTrue;

            public class FailedTests {
                @org.junit.Test
                public void failedTest() {
                    assertTrue(false);
                }
            }
        """
    }

    protected void flakyTest() {
        writeJavaTestSource """
            package acme;

            public class FlakyTests {
                @org.junit.Test
                public void flaky() {
                    ${flakyAssert()}
                }
            }
        """
    }

    enum DslExtensionType {

        DISTRIBUTION{
            @NamedVariant
            @Override
            String getSnippet(String result = 'true', boolean decorated = true, boolean addMethod = true) {
                """
                    interface TestDistributionExtension {}
                    class DefaultTestDistributionExtension implements TestDistributionExtension {
                        ${addMethod ? """
                        boolean shouldTestRetryPluginBeDeactivated() {
                            ${result}
                        }""" : ""}
                    }
                    ${decorated
                    ? 'test.extensions.create(TestDistributionExtension, "distribution", DefaultTestDistributionExtension)'
                    : 'test.extensions.add("distribution", new DefaultTestDistributionExtension())'}
                """
            }
        },

        DEVELOCITY{
            @NamedVariant
            @Override
            String getSnippet(String result = 'true', boolean decorated = true, boolean addMethod = true) {
                assert addMethod: 'not implemented'
                """
                    interface DevelocityTestConfiguration {
                        TestRetryConfiguration getTestRetry();
                    }
                    interface TestRetryConfiguration {
                    }
                    class DefaultDevelocityTestConfiguration implements DevelocityTestConfiguration {
                        TestRetryConfiguration testRetry = new DefaultTestRetryConfiguration()
                    }
                    class DefaultTestRetryConfiguration implements TestRetryConfiguration {
                        boolean shouldTestRetryPluginBeDeactivated() {
                            ${result}
                        }
                    }
                    ${decorated
                    ? 'test.extensions.create(DevelocityTestConfiguration, "develocity", DefaultDevelocityTestConfiguration)'
                    : 'test.extensions.add("develocity", new DefaultDevelocityTestConfiguration())'}
                """
            }
        };

        @NamedVariant
        abstract String getSnippet(String result = 'true', boolean decorated = true, boolean addMethod = true);

        @Override
        String toString() {
            name().toLowerCase(Locale.ROOT)
        }
    }
}
