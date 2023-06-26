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
    private final Set<Set<String>> acceptedCombinations

    /**
     * Default constructor.
     * <p>
     * This will automatically load {@code subprojects/internal-testing/src/main/resources/valid-precondition-combinations.csv}.
     */
    RequiresExtension() {
        this(PredicatesFile.DEFAULT_ACCEPTED_COMBINATIONS)
    }

    /**
     * Protected constructor (for testing).
     */
    protected RequiresExtension(Set<Set<String>> acceptedCombinations) {
        this.acceptedCombinations = acceptedCombinations
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
        def predicateClassNames = annotation.value() as List

        PredicatesFile.checkValidCombinations(predicateClassNames, acceptedCombinations)
        // If all preconditions are met, we DON'T skip the tests
        feature.skipped |= !TestPrecondition.allSatisfied(annotation.value())
    }

}
