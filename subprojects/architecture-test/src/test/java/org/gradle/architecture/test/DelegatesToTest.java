/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.architecture.test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.gradle.architecture.test.ArchUnitFixture.freeze;

/**
 * Tests if methods with {@code @DelegatesTo} annotation also has {@code @ClosureParams} annotation.
 */
@AnalyzeClasses(packages = "org.gradle")
public class DelegatesToTest {

    private static final DescribedPredicate<JavaMethod> contains_parameter_annotated_with_delegatesTo = new DescribedPredicate<JavaMethod>("contains parameter annotated with @DelegatesTo") {
        @Override
        public boolean test(JavaMethod javaMethod) {
            return javaMethod.getParameters().stream().anyMatch(
                param -> param.isAnnotatedWith(DelegatesTo.class)
            );
        }
    };

    private static ArchCondition<JavaMethod> beAnnotatedWithClosureParams() {
        return new ArchCondition<JavaMethod>("be annotated with @ClosureParams") {
            @Override
            public void check(JavaMethod javaMethod, ConditionEvents conditionEvents) {
                javaMethod.getParameters().stream().filter(
                    param -> param.isAnnotatedWith(DelegatesTo.class)
                ).forEach(
                    param -> {
                        if (!param.isAnnotatedWith(ClosureParams.class)) {
                            conditionEvents.add(
                                new SimpleConditionEvent(
                                    javaMethod,
                                    false,
                                    String.format(
                                        "Method %s has parameter #%d annotated with @DelegatesTo but not annotated with @ClosureParams",
                                        javaMethod.getFullName(),
                                        param.getIndex() + 1
                                    )
                                )
                            );
                        }
                    }
                );
            }
        };
    }

    @ArchTest
    public static final ArchRule methods_with_delegatesto_annotation_also_has_closureparams_annotation = freeze(
        methods()
            .that(contains_parameter_annotated_with_delegatesTo)
            .should(beAnnotatedWithClosureParams())
    );

}
