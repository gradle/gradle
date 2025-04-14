/*
 * Copyright 2025 the original author or authors.
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

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.provider.Provider;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaMember.Predicates.declaredIn;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static org.gradle.architecture.test.ArchUnitFixture.freeze;

@AnalyzeClasses(packages = "org.gradle")
public class PropertyUsageArchitectureTest {

    @ArchTest
    @SuppressWarnings("deprecation")
    public static final ArchRule task_implementations_should_define_properties_as_abstract_getters = freeze(fields()
        .that(are(declaredIn(assignableTo(Task.class))
            .and(not(declaredIn(Task.class)))
            .and(not(declaredIn(DefaultTask.class)))
            .and(not(declaredIn(org.gradle.api.internal.AbstractTask.class)))))
        .should(definePropertiesTheRightWay()));

    private static ArchCondition<JavaField> definePropertiesTheRightWay() {
        return new DefinePropertiesTheRightWay();
    }

    private static class DefinePropertiesTheRightWay extends ArchCondition<JavaField> {
        public DefinePropertiesTheRightWay() {
            super("define properties via abstract getters");
        }

        @Override
        public void check(JavaField field, ConditionEvents events) {
            JavaClass rawType = field.getRawType();
            boolean isExplicitlyInstantiatedProperty = rawType.isAssignableTo(Provider.class) ||
                rawType.isAssignableTo(ConfigurableFileCollection.class) ||
                rawType.isAssignableTo(ConfigurableFileTree.class);

            if (isExplicitlyInstantiatedProperty) {
                String message = String.format("%s is an explicitly instantiated property", field.getDescription());
                events.add(new SimpleConditionEvent(field, false, message));
            }
        }

    }

}
