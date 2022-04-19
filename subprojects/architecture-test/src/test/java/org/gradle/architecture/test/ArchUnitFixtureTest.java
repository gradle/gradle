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

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvent;
import com.tngtech.archunit.lang.ConditionEvents;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.gradle.architecture.test.ArchUnitFixture.haveOnlyArgumentsOrReturnTypesThatAre;
import static org.gradle.architecture.test.ArchUnitFixture.primitive;

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

    @SuppressWarnings({ "unused", "checkstyle:LeftCurly" })
    static class AllowedMethodTypesClass {
        public void validMethod(String arg1, String arg2) {}
        public File invalidReturnType(String arg1, String arg2) { return null; }
        public void invalidParameterType(String arg1, File arg2) {}
        public <T extends File> void invalidTypeParameterBoundType(String arg1, String arg2) {}
        public List<? extends File> invalidTypeParameterInReturnType(String arg1, String arg2) { return null; }
        public String[] validArrayTypeParameterInReturnType(String arg1, String arg2) { return null; }
        public File[] invalidArrayTypeParameterInReturnType(String arg1, String arg2) { return null; }
        public void invalidTypeParameterInParameterType(String arg1, List<File> arg2) { }
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
        ConditionEvents events = new ConditionEvents();
        archCondition.check(validMethod, events);
        assertThat(events).hasSize(1);
        return events.iterator().next();
    }

    private void assertNoViolation(ConditionEvent event) {
        assertThat(event.isViolation()).isFalse();
    }

    private static void assertHasViolation(ConditionEvent event, Class<?> violatedType) {
        assertThat(event.isViolation()).isTrue();
        String description = event.getDescriptionLines().get(0);
        assertThat(description).matches(String.format(".* has arguments/return type %s that is not allowed in .*", Pattern.quote(violatedType.getName())));
    }
}
