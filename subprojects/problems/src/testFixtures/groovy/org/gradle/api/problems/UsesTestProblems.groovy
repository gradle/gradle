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

package org.gradle.api.problems


import org.gradle.api.problems.internal.DefaultProblems
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.operations.OperationIdentifier

/**
 * Utility to be implemented by test classes that provides methods for creating [Problems] instances for testing.
 */
interface UsesTestProblems {
    /**
     * A [BuildOperationProgressEventEmitter] that does nothing.
     */
    static class NoOpBuildOperationProgressEventEmitter implements BuildOperationProgressEventEmitter {
        @Override
        void emit(OperationIdentifier operationIdentifier, long timestamp, Object details) {}

        @Override
        void emitNowIfCurrent(Object details) {}

        @Override
        void emitIfCurrent(long time, Object details) {}

        @Override
        void emitNowForCurrent(Object details) {}
    }

    /**
     * Creates a new [Problems] instance that does not report problems as build operation events.
     *
     * @return the problems instance
     */
    default Problems createTestProblems() {
        return new DefaultProblems(new NoOpBuildOperationProgressEventEmitter())
    }

//    default Problems createTestProblems2() {
//        def problemBuilder = new TestProblemBuilder()
//
//        def group = new TestProblemBuilderDefiningGroup(problemBuilder)
//
//        def type = new ProblemBuilderDefiningType() {
//            @Override
//            ProblemBuilderDefiningGroup type(String problemType) {
//                return group
//            }
//        }
//
//        def location = new ProblemBuilderDefiningLocation() {
//            @Override
//            ProblemBuilderDefiningType location(String path, Integer line) {
//                return type
//            }
//
//            @Override
//            ProblemBuilderDefiningType location(String path, Integer line, Integer column) {
//                return type
//            }
//
//            @Override
//            ProblemBuilderDefiningType noLocation() {
//                return type
//            }
//        }
//
//        def documentation = new ProblemBuilderDefiningDocumentation() {
//            @Override
//            ProblemBuilderDefiningLocation documentedAt(DocLink doc) {
//                return location
//            }
//
//            @Override
//            ProblemBuilderDefiningLocation undocumented() {
//                return location
//            }
//        }
//
//        def label = new ProblemBuilderDefiningLabel() {
//            @Override
//            ProblemBuilderDefiningDocumentation label(String label, Object... args) {
//                return documentation
//            }
//        }
//
//        return new Problems() {
//            @Override
//            ProblemBuilderDefiningLabel createProblemBuilder() {
//                return label
//            }
//
//            @Override
//            RuntimeException throwing(ProblemBuilderSpec action) {
//                return null
//            }
//
//            @Override
//            RuntimeException rethrowing(RuntimeException e, ProblemBuilderSpec action) {
//                return null
//            }
//        }
//    }
//
//    static class TestProblemBuilderDefiningGroup {
//        @Override
//        ProblemBuilder group(ProblemGroup group) {
//            return problemBuilder
//        }
//
//        @Override
//        ProblemBuilder group(String group) {
//            return problemBuilder
//        }
//    }
//
//    static class TestReportableProblem implements ReportableProblem {
//        @Override
//        void report() {
//        }
//
//        @Override
//        ProblemGroup getProblemGroup() {
//            return null
//        }
//
//        @Override
//        String getProblemType() {
//            return null
//        }
//
//        @Override
//        String getLabel() {
//            return null
//        }
//
//        @Override
//        String getDetails() {
//            return null
//        }
//
//        @Override
//        Severity getSeverity() {
//            return null
//        }
//
//        @Override
//        ProblemLocation getWhere() {
//            return null
//        }
//
//        @Override
//        DocLink getDocumentationLink() {
//            return null
//        }
//
//        @Override
//        List<String> getSolutions() {
//            return null
//        }
//
//        @Override
//        Throwable getCause() {
//            return null
//        }
//
//        @Override
//        Map<String, String> getAdditionalData() {
//            return null
//        }
//    }
//
//    static class TestProblemBuilder implements ProblemBuilder {
//        @Override
//        ProblemBuilder details(String details) {
//            return null
//        }
//
//        @Override
//        ProblemBuilder solution(String solution) {
//            return this
//        }
//
//        @Override
//        ProblemBuilder cause(Throwable cause) {
//            return this
//        }
//
//        @Override
//        ProblemBuilder additionalData(String key, String value) {
//            return this
//        }
//
//        @Override
//        ProblemBuilder withException(RuntimeException e) {
//            return this
//        }
//
//        @Override
//        ProblemBuilder severity(Severity severity) {
//            return this
//        }
//
//        @Override
//        ProblemBuilder withException(Throwable exception) {
//            return this
//        }
//
//        @Override
//        ReportableProblem build() {
//            return new TestReportableProblem()
//        }
//    }


//    default Problems mockProblems() {
//        def problemBuilder = Mock(ProblemBuilder.class)
//        problemBuilder.severity(_ as Severity) >> problemBuilder
//        problemBuilder.withException(_ as RuntimeException) >> problemBuilder
//        problemBuilder.build() >> Mock(ReportableProblem.class)
//
//        def group = Mock(ProblemBuilderDefiningGroup.class) {
//            group(_ as String) >> problemBuilder
//        }
//        def type = Mock(ProblemBuilderDefiningType.class) {
//            type(_ as String) >> group
//        }
//        def location = Mock(ProblemBuilderDefiningLocation.class) {
//            noLocation() >> type
//        }
//        def documentation = Mock(ProblemBuilderDefiningDocumentation.class) {
//            undocumented() >> location
//        }
//        def label = Mock(ProblemBuilderDefiningLabel.class) {
//            label(_ as String) >> documentation
//        }
//        def problems = Mock(Problems) {
//            createProblemBuilder() >> label
//        }
//
//        return problems
//    }
}
