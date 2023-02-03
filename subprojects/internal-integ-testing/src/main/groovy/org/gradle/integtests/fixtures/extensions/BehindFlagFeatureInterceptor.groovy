/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.fixtures.extensions

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import org.gradle.integtests.fixtures.executer.AbstractGradleExecuter
import org.spockframework.runtime.extension.IMethodInvocation

import java.lang.annotation.Annotation

/**
 * A base interceptor for features hidden behind a flag, convenient for executing tests with the flag on or off.
 * If a test only makes sense if the feature is enabled, then it needs to be annotated with {@link RequiredFeatures}.
 */
@CompileStatic
abstract class BehindFlagFeatureInterceptor extends AbstractMultiTestInterceptor {
    final Map<String, Feature> features

    BehindFlagFeatureInterceptor(Class<?> target, Map<String, Feature> features, boolean executeAllPermutations) {
        super(target, executeAllPermutations)
        this.features = features
    }

    BehindFlagFeatureInterceptor(Class<?> target, Map<String, Feature> features) {
        this(target, features, true)
    }

    static void extractRequiredFeatures(RequiredFeatures requires, Map<String, String> required) {
        requires.value().each { RequiredFeature feat ->
            required[feat.feature()] = feat.value()
        }
    }

    static Map<String, String> requiredFeatures(Annotation[] testAnnotations) {
        Map<String, String> required = [:]
        getAllAnnotations(testAnnotations, RequiredFeature).each { required[it.feature()] = it.value() }
        getAllAnnotations(testAnnotations, RequiredFeatures).each { extractRequiredFeatures(it, required) }
        required
    }

    static <A> Collection<A> getAllAnnotations(testAnnotations, Class<A> annotationType) {
        (Collection<A>) testAnnotations.findAll { annotationType.isAssignableFrom(it.getClass()) }
    }

    def isInvalidCombination(Map<String, String> values) {
        false
    }

    @Override
    protected void createExecutions() {
        def requiredFeatures = requiredFeatures(target.annotations)
        def allFeatures = features.values()
        def combinations = allFeatures.collect { it.displayNames.keySet() }.combinations()
        combinations.each {
            Map<String, String> executionValues = [:]
            Iterator<String> iter = it.iterator()
            boolean skip = false
            features.keySet().each { String key ->
                executionValues[key] = iter.next()
                if (requiredFeatures.containsKey(key) && executionValues[key] != requiredFeatures[key]) {
                    skip = true
                }
            }
            if (!skip && !isInvalidCombination(executionValues)) {
                add(new FeatureExecution(features, executionValues))
            }
        }
    }

    private static class FeatureExecution extends AbstractMultiTestInterceptor.Execution {
        final Map<String, Feature> features
        final Map<String, String> featureValues

        FeatureExecution(Map<String, Feature> features, Map<String, String> featureValues) {
            this.features = features
            this.featureValues = featureValues
        }

        @Override
        protected String getDisplayName() {
            featureValues.collect { sysProp, value ->
                "${features[sysProp].displayNames[value]}"
            }.join(", ")
        }

        @Override
        String toString() {
            return "[" + getDisplayName() + "]"
        }

        @Override
        protected void before(IMethodInvocation invocation) {
            featureValues.each { sysProp, value ->
                // Ensure that the system property is propagated to forked Gradle executions
                AbstractGradleExecuter.propagateSystemProperty(sysProp)
                System.setProperty(sysProp, value)
            }
        }

        @Override
        protected void after() {
            featureValues.each { sysProp, value ->
                // Stop propagating this system property
                AbstractGradleExecuter.doNotPropagateSystemProperty(sysProp)
                System.properties.remove(sysProp)
            }
        }

        @Override
        boolean isTestEnabled(AbstractMultiTestInterceptor.TestDetails testDetails) {
            def requiredFeatures = requiredFeatures(testDetails.annotations)
            def enabled = true
            requiredFeatures.each { sysProp, value ->
                if (featureValues[sysProp] != value) {
                    enabled = false
                }
            }
            return enabled
        }
    }

    static class Feature {
        final Map<String, String> displayNames

        Feature(Map<String, String> displayNames) {
            this.displayNames = displayNames
        }
    }

    static Feature booleanFeature(String name) {
        new Feature(['true': "with $name".toString(), 'false': "without $name".toString()])
    }
}
