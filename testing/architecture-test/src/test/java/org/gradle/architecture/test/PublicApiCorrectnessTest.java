/*
 * Copyright 2021 the original author or authors.
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
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.Plugin;
import org.gradle.api.Task;
import org.gradle.api.specs.Spec;

import java.util.List;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.implement;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.conditions.ArchConditions.not;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.members;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.gradle.architecture.test.ArchUnitFixture.beAbstractClass;
import static org.gradle.architecture.test.ArchUnitFixture.freeze;
import static org.gradle.architecture.test.ArchUnitFixture.gradleInternalApi;
import static org.gradle.architecture.test.ArchUnitFixture.gradlePublicApi;
import static org.gradle.architecture.test.ArchUnitFixture.groovyApi;
import static org.gradle.architecture.test.ArchUnitFixture.haveDirectSuperclassOrInterfaceThatAre;
import static org.gradle.architecture.test.ArchUnitFixture.haveOnlyArgumentsOrReturnTypesThatAre;
import static org.gradle.architecture.test.ArchUnitFixture.not_from_fileevents;
import static org.gradle.architecture.test.ArchUnitFixture.not_written_in_kotlin;
import static org.gradle.architecture.test.ArchUnitFixture.overrideMethod;
import static org.gradle.architecture.test.ArchUnitFixture.primitive;
import static org.gradle.architecture.test.ArchUnitFixture.public_api_methods;
import static org.gradle.architecture.test.ArchUnitFixture.useJSpecifyNullable;
import static org.gradle.architecture.test.PermittedPublicApiTypes.*;

@AnalyzeClasses(packages = "org.gradle")
public class PublicApiCorrectnessTest {

    // The permitted non-Gradle types are the source of truth in PermittedPublicApiTypes.
    private static final DescribedPredicate<JavaClass> allowed_types_for_public_api =
        gradlePublicApi()
            .or(primitive)
            .or(resideInAnyPackage(PERMITTED_JDK_PACKAGES.toArray(new String[0]))
                .or(anyOf(PERMITTED_JDK_TYPES))
                .as("built-in JDK classes"))
            .or(anyOf(PERMITTED_KOTLIN_TYPES).as("Kotlin classes"))
            .or(anyOf(PERMITTED_SLF4J_TYPES).as("slf4j classes"));

    private static DescribedPredicate<JavaClass> anyOf(List<Class<?>> types) {
        return types.stream()
            .map(JavaClass.Predicates::type)
            .reduce((left, right) -> left.or(right))
            .orElseThrow(() -> new IllegalArgumentException("At least one type must be provided"));
    }

    private static final DescribedPredicate<JavaClass> public_api_tasks_or_plugins =
            gradlePublicApi().and(assignableTo(Task.class).or(assignableTo(Plugin.class)));

    @ArchTest
    public static final ArchRule public_api_methods_do_not_reference_internal_types_as_parameters = freeze(members()
        .that(are(public_api_methods).and(DescribedPredicate.not(describe("constructors", m -> m instanceof JavaConstructor))))
        .should(haveOnlyArgumentsOrReturnTypesThatAre(allowed_types_for_public_api))
    );

    @ArchTest
    public static final ArchRule public_api_methods_with_closures = freeze(methods()
        .that(are(public_api_methods))
        .should(new ArchUnitFixture.HaveGradleTypeEquivalent())
    );

    @ArchTest
    public static final ArchRule public_api_tasks_and_plugins_are_abstract = classes()
            .that(are(public_api_tasks_or_plugins))
            .should(beAbstractClass());

    @ArchTest
    public static final ArchRule public_api_classes_do_not_extend_internal_types = freeze(classes()
        .that(are(gradlePublicApi()))
        .should(not(
            haveDirectSuperclassOrInterfaceThatAre(gradleInternalApi()).or(haveDirectSuperclassOrInterfaceThatAre(groovyApi()))
        ))
    );

    /**
     * Code written in Kotlin implicitly uses {@link org.jetbrains.annotations.Nullable}, so
     * those packages are excluded from this check.
     */
    @ArchTest
    public static final ArchRule all_methods_use_proper_Nullable = methods()
            .that(are(not_written_in_kotlin).and(are(not_from_fileevents)))
            .should(useJSpecifyNullable()
    );

    @ArchTest
    public static final ArchRule named_domain_object_collection_implementations_override_named_method = classes()
        .that(implement(NamedDomainObjectCollection.class))
        .should(overrideMethod("named", new Class<?>[] {Spec.class}, NamedDomainObjectCollection.class));

    @ArchTest
    public static final ArchRule contract_annotations_not_used_in_public_api = codeUnits()
        .that(are(public_api_methods))
        .should(notBeAnnotatedWith(jetbrainsContractAnnotation()));

    private static ArchCondition<JavaCodeUnit> notBeAnnotatedWith(DescribedPredicate<JavaAnnotation<?>> annotations) {
        return new ArchCondition<>("not be annotated with " + annotations.getDescription()) {
            @Override
            public void check(JavaCodeUnit item, ConditionEvents events) {
                if (item.getAnnotations().stream().anyMatch(annotations)) {
                    events.add(new SimpleConditionEvent(item, false, item.getFullName() + " is annotated with " + annotations.getDescription()));
                }
            }
        };
    }

    private static DescribedPredicate<JavaAnnotation<?>> jetbrainsContractAnnotation() {
        return new DescribedPredicate<>("JetBrains @Contract annotation") {
            @Override
            public boolean test(JavaAnnotation<?> javaAnnotation) {
                return "org.jetbrains.annotations.Contract".equals(javaAnnotation.getRawType().getName());
            }
        };
    }
}
