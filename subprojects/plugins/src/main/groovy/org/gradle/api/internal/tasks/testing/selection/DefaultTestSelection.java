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
package org.gradle.api.internal.tasks.testing.selection;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.testing.TestSelection;
import org.gradle.api.tasks.testing.TestSelectionSpec;
import org.gradle.internal.typeconversion.*;

import java.util.*;

public class DefaultTestSelection implements TestSelection {

    private Set<TestSelectionSpec> includedTests = new HashSet<TestSelectionSpec>();

    public DefaultTestSelection includeTest(String testClass, String testMethod) {
        includedTests.add(new DefaultTestSelectionSpec(testClass, testMethod));
        return this;
    }

    @Input
    public Set<TestSelectionSpec> getIncludedTests() {
        return includedTests;
    }

    public void setIncludedTests(Object ... includedTests) {
        this.includedTests = new NotationParserBuilder<TestSelectionSpec>()
            .resultingType(TestSelectionSpec.class)
            .parser(new TestSelectionSpecParser())
            .withDefaultJustReturnParser(false) //client implementations may not be Serializable
            .invalidNotationMessage("Unable to configure the test inclusion criteria.")
            .toFlatteningComposite().parseNotation(includedTests);
    }

    private static class TestSelectionSpecParser implements NotationParser<Object, TestSelectionSpec> {
        public TestSelectionSpec parseNotation(Object notation) throws UnsupportedNotationException, TypeConversionException {
            if (notation instanceof TestSelectionSpec) {
                TestSelectionSpec spec = (TestSelectionSpec) notation;
                return new DefaultTestSelectionSpec(spec.getClassPattern(), spec.getMethodPattern());
            }
            throw new UnsupportedNotationException(notation);
        }

        public void describe(Collection<String> candidateFormats) {
            candidateFormats.add("Instances of " + TestSelectionSpec.class.getSimpleName());
        }
    }
}