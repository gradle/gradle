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

package org.gradle.docs.samples;

import org.gradle.exemplar.model.Sample;
import org.junit.runners.model.InitializationError;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

abstract class PartitioningSamplesRunner extends IntegrationTestSamplesRunner {
    public static class SamplesBucket extends PartitioningSamplesRunner {
        public SamplesBucket(Class<?> testClass) throws InitializationError {
            super(testClass);
        }

        @Override
        Predicate<String> sampleIdFilter() {
            return SAMPLES_BUCKET;
        }
    }

    public static class SnippetsBucket1 extends PartitioningSamplesRunner {
        public SnippetsBucket1(Class<?> testClass) throws InitializationError {
            super(testClass);
        }

        @Override
        Predicate<String> sampleIdFilter() {
            return SNIPPETS_BUCKET_1;
        }
    }

    public static class SnippetsBucket2 extends PartitioningSamplesRunner {
        public SnippetsBucket2(Class<?> testClass) throws InitializationError {
            super(testClass);
        }

        @Override
        Predicate<String> sampleIdFilter() {
            return SNIPPETS_BUCKET_2;
        }
    }

    public static class SnippetsBucket3 extends PartitioningSamplesRunner {
        public SnippetsBucket3(Class<?> testClass) throws InitializationError {
            super(testClass);
        }

        @Override
        Predicate<String> sampleIdFilter() {
            return SNIPPETS_BUCKET_3;
        }
    }

    private static final Predicate<String> SAMPLES_BUCKET = s -> !s.startsWith("snippet-");
    private static final Predicate<String> SNIPPETS_BUCKET_1 = s ->
        s.startsWith("snippet-dependency-management-") ||
            s.startsWith("snippet-tutorial-") ||
            s.startsWith("snippet-kotlin-dsl") ||
            s.startsWith("snippet-testing-") ||
            s.startsWith("snippet-scala-");
    private static final Predicate<String> SNIPPETS_BUCKET_2 = s ->
        s.startsWith("snippet-configuration-cache-") ||
            s.startsWith("snippet-test-kit-") ||
            s.startsWith("snippet-java-") ||
            s.startsWith("snippet-maven-") ||
            s.startsWith("snippet-plugins-") ||
            s.startsWith("snippet-tasks-") ||
            s.startsWith("snippet-files-");
    private static final Predicate<String> SNIPPETS_BUCKET_3 = s ->
        !SAMPLES_BUCKET.test(s) && !SNIPPETS_BUCKET_1.test(s) && !SNIPPETS_BUCKET_2.test(s);

    abstract Predicate<String> sampleIdFilter();

    public PartitioningSamplesRunner(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    protected List<Sample> getChildren() {
        List<Sample> allSamples = super.getChildren();
        return allSamples.stream()
            .filter(s -> sampleIdFilter().test(s.getId()))
            .collect(Collectors.toList());
    }
}
