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

import com.google.common.collect.ImmutableList;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvent;
import com.tngtech.archunit.lang.ConditionEvents;
import org.gradle.api.NonNullApi;
import org.gradlebuild.AbstractClass;
import org.gradlebuild.AllowedMethodTypesClass;
import org.gradlebuild.ConcreteClass;
import org.gradlebuild.Interface;
import org.gradlebuild.WrongNullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.gradle.architecture.test.ArchUnitFixture.haveOnlyArgumentsOrReturnTypesThatAre;
import static org.gradle.architecture.test.ArchUnitFixture.primitive;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ArchUnitFixtureTest {
    @Test
    public void reports_valid_methods() {
        ConditionEvent event = checkThatHasOnlyAllowedTypes("validMethod");

        assertNoViolation(event);
    }

    @Test
    public void reports_invalid_return_types() {
        ConditionEvent event = checkThatHasOnlyAllowedTypes("invalidReturnType");

        assertHasViolation(event, File.class);
    }

    @Test
    public void reports_invalid_parameter_types() {
        ConditionEvent event = checkThatMethodHasOnlyAllowedArgumentTypesOrReturnTypes("invalidParameterType", String.class, File.class);

        assertHasViolation(event, File.class);
    }

    @Test
    public void reports_invalid_generic_type_parameter_bound() {
        ConditionEvent event = checkThatHasOnlyAllowedTypes("invalidTypeParameterBoundType");

        assertHasViolation(event, File.class);
    }

    @Test
    public void reports_invalid_generic_type_parameter_of_return_type() {
        ConditionEvent event = checkThatHasOnlyAllowedTypes("invalidTypeParameterInReturnType");

        assertHasViolation(event, File.class);
    }

    @Test
    public void array_types_are_allowed() {
        ConditionEvent event = checkThatHasOnlyAllowedTypes("validArrayTypeParameterInReturnType");

        assertNoViolation(event);
    }

    @Test
    public void reports_invalid_array_types_return_type() {
        ConditionEvent event = checkThatHasOnlyAllowedTypes("invalidArrayTypeParameterInReturnType");

        assertHasViolation(event, File[].class);
    }

    @Test
    public void reports_invalid_generic_type_of_parameter() {
        ConditionEvent event = checkThatMethodHasOnlyAllowedArgumentTypesOrReturnTypes("invalidTypeParameterInParameterType", String.class, List.class);

        // TODO: Should detect the violation
        Assertions.assertThrows(AssertionError.class, () -> assertHasViolation(event, File.class));
    }

    @Test
    public void accepts_interfaces_as_abstract_classes() {
        ConditionEvent event = checkClassCondition(ArchUnitFixture.beAbstract(), Interface.class);
        assertNoViolation(event);
    }

    @Test
    public void accepts_abstract_classes() {
        ConditionEvent event = checkClassCondition(ArchUnitFixture.beAbstract(), AbstractClass.class);
        assertNoViolation(event);
    }

    @Test
    public void reports_non_abstract_classes() {
        ConditionEvent event = checkClassCondition(ArchUnitFixture.beAbstract(), ConcreteClass.class);
        assertThat(event.isViolation()).isTrue();
        assertThat(eventDescription(event)).isEqualTo("org.gradlebuild.ConcreteClass is not abstract");
    }

    @Test
    public void checks_for_nullable_annotation() {
        ConditionEvents events = checkMethodCondition(ArchUnitFixture.useJavaxAnnotationNullable(), WrongNullable.class);
        assertTrue(events.containViolation());
        assertThat(events.getViolating().size()).isEqualTo(2);
        List<String> descriptions = events.getViolating().stream().map(ArchUnitFixtureTest::eventDescription).collect(Collectors.toList());
        assertThat(descriptions).containsExactlyInAnyOrder(
                "org.gradlebuild.WrongNullable.returnsNull() is using forbidden Nullable annotations: org.jetbrains.annotations.Nullable",
                "parameter 0 for org.gradlebuild.WrongNullable.acceptsNull(java.lang.String) is using forbidden Nullable annotations: org.jetbrains.annotations.Nullable"
        );
    }

    @Test
    public void checks_for_annotation_presence() {
        ArchCondition<JavaClass> condition = ArchUnitFixture.beAnnotatedOrInPackageAnnotatedWith(NonNullApi.class);
        assertNoViolation(checkClassCondition(condition, org.gradlebuild.nonnullapi.notinpackage.OwnNonNullApi.class));
        ConditionEvent event = checkClassCondition(condition, org.gradlebuild.nonnullapi.notinpackage.NoOwnNonNullApi.class);
        assertTrue(event.isViolation());
        assertThat(eventDescription(event)).startsWith("Class <org.gradlebuild.nonnullapi.notinpackage.NoOwnNonNullApi> is not annotated (directly or via its package) with @org.gradle.api.NonNullApi");
        // Cannot test on-package (not on the class) annotation, due to `ClasFileImporter` limitations
    }

    private static String eventDescription(ConditionEvent event) {
        return String.join(" ", event.getDescriptionLines());
    }

    private ConditionEvents checkMethodCondition(ArchCondition<JavaMethod> archCondition, Class<?> clazz) {
        JavaClass javaClass = new ClassFileImporter().importClass(clazz);
        CollectingConditionEvents events = new CollectingConditionEvents();
        for (JavaMethod method : javaClass.getAllMethods()) {
            archCondition.check(method, events);
        }
        return events;
    }

    private ConditionEvent checkClassCondition(ArchCondition<JavaClass> archCondition, Class<?> clazz) {
        JavaClass javaClass = new ClassFileImporter().importClass(clazz);
        CollectingConditionEvents events = new CollectingConditionEvents();
        archCondition.check(javaClass, events);
        assertThat(events.getAllEvents()).hasSize(1);
        return events.getAllEvents().iterator().next();
    }

    @Nonnull
    private ConditionEvent checkThatHasOnlyAllowedTypes(String methodName) {
        return checkThatMethodHasOnlyAllowedArgumentTypesOrReturnTypes(methodName, String.class, String.class);
    }

    @Nonnull
    private ConditionEvent checkThatMethodHasOnlyAllowedArgumentTypesOrReturnTypes(String methodName, Class<?>... arguments) {
        ArchCondition<JavaMethod> archCondition = haveOnlyArgumentsOrReturnTypesThatAre(resideInAnyPackage("java.lang").or(primitive).or(resideInAnyPackage("java.util")).as("allowed"));
        JavaClass javaClass = new ClassFileImporter().importClass(AllowedMethodTypesClass.class);
        JavaMethod validMethod = javaClass.getMethod(methodName, arguments);
        CollectingConditionEvents events = new CollectingConditionEvents();
        archCondition.check(validMethod, events);
        assertThat(events.getAllEvents()).hasSize(1);
        return events.getAllEvents().iterator().next();
    }

    private void assertNoViolation(ConditionEvent event) {
        assertThat(event.isViolation()).isFalse();
    }

    private static void assertHasViolation(ConditionEvent event, Class<?> violatedType) {
        assertThat(event.isViolation()).isTrue();
        String description = event.getDescriptionLines().get(0);
        assertThat(description).matches(String.format(".* has arguments/return type %s that is not allowed in .*", Pattern.quote(violatedType.getName())));
    }

    private static class CollectingConditionEvents implements ConditionEvents {
        private final List<ConditionEvent> events = new ArrayList<>();
        private Optional<String> informationAboutNumberOfViolations = Optional.empty();

        @Override
        public void add(ConditionEvent event) {
            events.add(event);
        }

        @Override
        public Optional<String> getInformationAboutNumberOfViolations() {
            return informationAboutNumberOfViolations;
        }

        @Override
        public void setInformationAboutNumberOfViolations(String informationAboutNumberOfViolations) {
            this.informationAboutNumberOfViolations = Optional.of(informationAboutNumberOfViolations);
        }

        @Override
        public Collection<ConditionEvent> getViolating() {
            return events.stream().filter(ConditionEvent::isViolation).collect(Collectors.toList());
        }

        @Override
        public boolean containViolation() {
            return events.stream().anyMatch(ConditionEvent::isViolation);
        }

        public Collection<ConditionEvent> getAllEvents() {
            return ImmutableList.copyOf(events);
        }
    }
}
