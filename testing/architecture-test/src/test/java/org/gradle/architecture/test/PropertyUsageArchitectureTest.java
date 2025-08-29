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

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.conditions.ArchPredicates;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.options.OptionValues;

import javax.inject.Inject;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaMember.Predicates.declaredIn;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.gradle.architecture.test.ArchUnitFixture.beAbstractMethod;
import static org.gradle.architecture.test.ArchUnitFixture.freeze;

@AnalyzeClasses(packages = "org.gradle")
public class PropertyUsageArchitectureTest {

    private static final DescribedPredicate<JavaMethod> hasRichPropertyReturnType = new DescribedPredicate<JavaMethod>("hasRichPropertyReturnType") {
        @Override
        public boolean test(JavaMethod method) {
            JavaClass returnType = method.getRawReturnType();
            return returnType.isAssignableTo(Provider.class) || returnType.isAssignableTo(FileCollection.class);
        }
    };

    private static final DescribedPredicate<JavaMethod> isPrivate = new DescribedPredicate<JavaMethod>("isPrivate") {
        @Override
        public boolean test(JavaMethod method) {
            return method.getModifiers().contains(JavaModifier.PRIVATE);
        }
    };

    @SuppressWarnings({"deprecation", "UnnecessaryFullyQualifiedName"})
    private static final DescribedPredicate<JavaMethod> rich_task_property_getters = ArchPredicates.<JavaMethod>are(declaredIn(assignableTo(Task.class)))
        .and(are(ArchUnitFixture.getters))
        .and(are(hasRichPropertyReturnType))
        .and(are(not(isPrivate)))
        .and(not(annotatedWith(Inject.class)))
        .and(not(annotatedWith(OptionValues.class)))
        .and(not(declaredIn(Task.class)))
        .and(not(declaredIn(DefaultTask.class)))
        .and(not(declaredIn(org.gradle.api.internal.AbstractTask.class)))
        .as("task properties");

    @ArchTest
    public static final ArchRule task_implementations_should_define_properties_as_abstract_getters = freeze(methods()
        .that(are(rich_task_property_getters))
        .should(beAbstractMethod()));

}
