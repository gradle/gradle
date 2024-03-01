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

package org.gradle.internal.logging.console.jvm

import org.gradle.integtests.fixtures.RichConsoleStyling
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.test.fixtures.server.http.BlockingHttpServer

class TestedProjectFixture {

    static String testClass(String testAnnotationClassName, String testClassName, String serverResource, BlockingHttpServer server) {
        """
            package org.gradle;

            import ${testAnnotationClassName};

            public class $testClassName {
                @Test
                public void longRunningTest() {
                    ${server.callFromBuild(serverResource)}
                }
            }
        """
    }

    static void containsTestExecutionWorkInProgressLine(GradleHandle gradleHandle, String taskPath, String testName) {
        RichConsoleStyling.assertHasWorkInProgress(gradleHandle, "> $taskPath > Executing test $testName")
    }

    static class JavaTestClass {
        public static final PRESERVED_TEST1 = new JavaTestClass('org.gradle.Test1', 'org.gradle.Test1')
        public static final PRESERVED_TEST2 = new JavaTestClass('org.gradle.Test2', 'org.gradle.Test2')
        public static final SHORTENED_TEST1 = new JavaTestClass('org.gradle.AdvancedJavaPackageAbbreviatingClassFunctionalTest', 'org...AdvancedJavaPackageAbbreviatingClassFunctionalTest')
        public static final SHORTENED_TEST2 = new JavaTestClass('org.gradle.EvenMoreAdvancedJavaPackageAbbreviatingJavaClassFunctionalTest', '...EvenMoreAdvancedJavaPackageAbbreviatingJavaClassFunctionalTest')

        private final String fullyQualifiedClassName
        private final String renderedClassName

        JavaTestClass(String fullyQualifiedClassName, String renderedClassName) {
            this.fullyQualifiedClassName = fullyQualifiedClassName
            this.renderedClassName = renderedClassName
        }

        String getFileRepresentation() {
            fullyQualifiedClassName.replace('.', '/') + '.java'
        }

        String getClassNameWithoutPackage() {
            fullyQualifiedClassName.substring(fullyQualifiedClassName.lastIndexOf('.') + 1, fullyQualifiedClassName.length())
        }

        String getRenderedClassName() {
            renderedClassName
        }
    }
}
