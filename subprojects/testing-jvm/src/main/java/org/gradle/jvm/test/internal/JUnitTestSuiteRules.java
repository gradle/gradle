/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.jvm.test.internal;

import org.gradle.jvm.test.JUnitTestSuiteBinarySpec;
import org.gradle.jvm.test.JUnitTestSuiteSpec;
import org.gradle.model.Defaults;
import org.gradle.model.RuleSource;
import org.gradle.model.Validate;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.InvalidModelException;
import org.gradle.platform.base.internal.DefaultModuleDependencySpec;

import static org.gradle.model.internal.core.ModelNodes.withType;
import static org.gradle.model.internal.core.NodePredicate.allDescendants;

@SuppressWarnings("UnusedDeclaration")
public class JUnitTestSuiteRules extends RuleSource {

    public static void apply(ModelRegistry modelRegistry) {
        modelRegistry.getRoot().applyTo(allDescendants(withType(JUnitTestSuiteSpec.class)), JUnitTestSuiteRules.SpecRules.class);
        modelRegistry.getRoot().applyTo(allDescendants(withType(JUnitTestSuiteBinarySpec.class)), JUnitTestSuiteRules.BinaryRules.class);
    }

    static class SpecRules extends RuleSource {
        @Validate
        void validateJUnitVersion(JUnitTestSuiteSpec jUnitTestSuiteSpec) {
            if (jUnitTestSuiteSpec.getjUnitVersion() == null) {
                throw new InvalidModelException(
                    String.format("Test suite '%s' doesn't declare JUnit version. Please specify it with `jUnitVersion '4.12'` for example.", jUnitTestSuiteSpec.getName()));
            }
        }
    }

    static class BinaryRules extends RuleSource {
        @Defaults
        void configureBinaryJUnitVersion(JUnitTestSuiteBinarySpec testSuiteBinary) {
            JUnitTestSuiteSpec testSuite = testSuiteBinary.getTestSuite();
            String jUnitVersion = testSuite.getjUnitVersion();
            testSuiteBinary.setjUnitVersion(jUnitVersion);
            testSuiteBinary.getDependencies().add(junitDependencySpec(jUnitVersion));
        }

        private static DependencySpec junitDependencySpec(String jUnitVersion) {
            return new DefaultModuleDependencySpec("junit", "junit", jUnitVersion);
        }
    }
}
