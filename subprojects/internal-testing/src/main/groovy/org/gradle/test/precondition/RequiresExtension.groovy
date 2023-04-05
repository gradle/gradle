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
package org.gradle.test.precondition

import groovy.transform.CompileStatic
import org.gradle.test.fixtures.condition.PredicatesFile
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.spockframework.runtime.extension.ExtensionException
import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecElementInfo
import org.spockframework.runtime.model.SpecInfo

/**
 * Test extension enforcing the {@link Requires} annotation in Spock (note, that this is a separate class from Spock's own {@link spock.lang.Requires}).
 * <p>
 * If you need a new combination of {@link TestPrecondition} classes, go to {@code subprojects/internal-testing/src/main/resources/valid-precondition-combinations.csv} and simply add it.
 * <p>
 * See the <a href="https://github.com/gradle/gradle/tree/master/subprojects/predicate-tester">predicate-tester</a> project to learn about where we use the information.
 *
 * @see Requires
 */
@CompileStatic
class RequiresExtension implements IAnnotationDrivenExtension<Requires> {

    private final List<Matcher> acceptedCombinations

    /**
     * Default constructor.
     * <p>
     * This will automatically load {@code subprojects/internal-testing/src/main/resources/valid-precondition-combinations.csv}.
     */
    RequiresExtension() {
        this(PredicatesFile.readCombinationsFile("/valid-precondition-combinations.csv"))
    }

    /**
     * Protected constructor (for testing).
     */
    protected RequiresExtension(List<String[]> values) {
        acceptedCombinations = values.collect {
            Matchers.contains(
                it.collect { Matchers.equalTo(it) }
            )
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void visitSpecAnnotation(Requires annotation, SpecInfo spec) {
        visitAnnotation(annotation, spec)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void visitFeatureAnnotation(Requires annotation, FeatureInfo feature) {
        visitAnnotation(annotation, feature)
    }

    /**
     * Common method for handling annotations.
     */
    void visitAnnotation(Requires annotation, SpecElementInfo feature) {


        def predicateClassNames = annotation.value().collect {
            it.canonicalName
        }

        checkValidCombinations(predicateClassNames)
        // If all preconditions are met, we DON'T skip the tests
        feature.skipped |= !TestPrecondition.doSatisfiesAll(annotation.value())
    }

    /**
     * Checks if a precondition is present in the allowed precondition list stored
     * in {@code subprojects/internal-testing/src/main/resources/valid-precondition-combinations.csv}
     *
     * @param testPreconditions
     */
    protected void checkValidCombinations(List<String> predicateClassNames) {
        def found = Matchers.anyOf(acceptedCombinations).matches(predicateClassNames)

        if (!found) {
            def message = String.format(
                "Requested requirements [%s] were not in the list of accepted combinations. " +
                    "Add it to 'subprojects/internal-testing/src/main/resources/valid-precondition-combinations.csv' to be accepted. " +
                    "See the documentation of this class to learn more about this feature.",
                predicateClassNames.join(", ")
            )
            throw new ExtensionException(message)
        }
    }

}
