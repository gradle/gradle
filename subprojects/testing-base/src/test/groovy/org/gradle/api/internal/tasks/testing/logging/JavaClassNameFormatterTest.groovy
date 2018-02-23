/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.logging

import spock.lang.Specification

class JavaClassNameFormatterTest extends Specification {
    def "abbreviates long java packages"() {
        expect:
        JavaClassNameFormatter.abbreviateJavaPackage("", 60) == ""
        JavaClassNameFormatter.abbreviateJavaPackage("org.gradle.FooTest", 60) == "org.gradle.FooTest"
        JavaClassNameFormatter.abbreviateJavaPackage("TestNoPackageFunctionalIntegrationTestWithAReallyReallyLongName", 60) == "TestNoPackageFunctionalIntegrationTestWithAReallyReallyLongName"
        JavaClassNameFormatter.abbreviateJavaPackage("org.gradle.api.internal.tasks.testing.logging.TestWorkerProgressListenerTest", 60) == "org.gradle...testing.logging.TestWorkerProgressListenerTest"
        JavaClassNameFormatter.abbreviateJavaPackage("short.pkg.TestWorkerProgressListenerIntegrationTestWithAReallyReallyLongName", 60) == "...TestWorkerProgressListenerIntegrationTestWithAReallyReallyLongName"
    }
}
