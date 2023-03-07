/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.test.fixtures.condition

import groovy.transform.CompileStatic
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.spockframework.runtime.extension.ExtensionException
import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecElementInfo
import org.spockframework.runtime.model.SpecInfo

import java.nio.charset.StandardCharsets
import java.util.stream.Collectors

/**
 * Test extension enforcing the {@link Requires} annotation in Spock.
 *
 * <p>
 * <b>Defining new combinations</b><br/>
 * If you a new combination of {@link TestPrecondition} class, go to
 * {@code subprojects/internal-testing/src/main/resources/valid-precondition-combinations.csv}
 * and simply add it.
 *
 * The combination <i>might</i> fail, if the CI infrastructure lacks that particular combination.
 * In that case, contact
 * <a href="https://github.com/orgs/gradle/teams/bt-developer-productivity">@bt-developer-productivity</a>
 * for help.
 *
 * <p>
 * <b>Reason d'être</b><br/>
 * Defining the combinations allows us to test if we have all the required conditions set-up on the CI infrastructure.
 * Otherwise, situations can happen where tests with particular combinations are never going to be executed.
 *
 * @see <a href="https://github.com/gradle/gradle-private/issues/3616">#3616</a>
 */
@CompileStatic
class RequiresExtension implements IAnnotationDrivenExtension<Requires> {

    private final List<Matcher> acceptedCombinations

    /**
     * Default constructor.
     *
     * <p>
     * This will automatically load {@code subprojects/internal-testing/src/main/resources/valid-precondition-combinations.csv}.
     */
    RequiresExtension() {
        this(readCombinationsFile("/valid-precondition-combinations.csv"))
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
        def predicateClassNames = annotation.value().collect { it.name }

        checkValidCombinations(predicateClassNames)
        // If all preconditions are met, we DON'T skip the tests
        feature.skipped |= !TestPrecondition.doSatisfiesAll(annotation.value())
    }

    /**
     * Checks if a precondition is accepted by any of the accepted combinations.
     *
     * @param testPreconditions
     */
    protected void checkValidCombinations(List<String> predicateClassNames) {
        def found = Matchers.anyOf(acceptedCombinations).matches(predicateClassNames)

        if (!found) {
            def message = String.format(
                "Requested requirements [%s] were not in the list of accepted combinations. See RequiresExtension for help.",
                predicateClassNames.join(", ")
            )
            throw new ExtensionException(message)
        }
    }

    /**
     * Reads a sudo-csv file containing comma-separated values.
     * <p>
     * The csv is sudo in the sense, that you can comment by using "#"
     *
     * @param resource the resource file being used
     * @return a list of string arrays representing the values in the file
     */
    private static List<String[]> readCombinationsFile(String resource) {
        def lineStream = new BufferedReader(
            new InputStreamReader(
                RequiresExtension.getResourceAsStream(resource),
                StandardCharsets.UTF_8
            )
        ).lines()

        return lineStream.filter {
            !it.startsWith("#")
        }.map {
            it.trim()
        }.filter {
            !it.isEmpty()
        }.map {
            it.split(",")
        }.collect(Collectors.toList())
    }

}
