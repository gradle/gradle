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

package org.gradle.architecture.test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.provider.Provider;
import org.gradle.api.resources.TextResource;
import org.gradle.internal.reflect.PropertyAccessorType;

import javax.inject.Inject;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.core.domain.JavaMember.Predicates.declaredIn;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.gradle.architecture.test.ArchUnitFixture.public_api_methods;

@AnalyzeClasses(packages = "org.gradle")
public class ProviderMigrationArchitectureTest {
    private static final DescribedPredicate<JavaMethod> getters = new DescribedPredicate<JavaMethod>("getters") {
        @Override
        public boolean apply(JavaMethod input) {
            PropertyAccessorType accessorType = PropertyAccessorType.fromName(input.getName());
            return accessorType == PropertyAccessorType.GET_GETTER || accessorType == PropertyAccessorType.IS_GETTER;
        }
    };

    @SuppressWarnings("deprecation")
    @ArchTest
    public static final ArchRule public_api_task_properties_are_providers = methods()
        .that(are(public_api_methods))
        .and(are(declaredIn(assignableTo(Task.class))))
        .and(are(getters))
        .and().areNotDeclaredIn(Task.class)
        .and().areNotDeclaredIn(DefaultTask.class)
        .and().areNotDeclaredIn(AbstractTask.class)
        .and().areNotAnnotatedWith(Inject.class)
        .and().doNotHaveRawReturnType(TextResource.class)
        .and().doNotHaveRawReturnType(assignableTo(FileCollection.class))
        .should().haveRawReturnType(assignableTo(Provider.class));

    @SuppressWarnings("deprecation")
    @ArchTest
    public static final ArchRule public_api_task_file_properties_are_configurable_file_collections = methods()
        .that(are(public_api_methods))
        .and(are(declaredIn(assignableTo(Task.class))))
        .and(are(getters))
        .and().areNotDeclaredIn(Task.class)
        .and().areNotDeclaredIn(DefaultTask.class)
        .and().areNotDeclaredIn(AbstractTask.class)
        .and().areNotAnnotatedWith(Inject.class)
        .and().haveRawReturnType(assignableTo(FileCollection.class))
        .should().haveRawReturnType(assignableTo(ConfigurableFileCollection.class));

    @SuppressWarnings("deprecation")
    @ArchTest
    public static final ArchRule public_api_task_properties_should_not_use_text_resources = methods()
        .that(are(public_api_methods))
        .and(are(declaredIn(assignableTo(Task.class))))
        .and(are(getters))
        .and().areNotDeclaredIn(Task.class)
        .and().areNotDeclaredIn(DefaultTask.class)
        .and().areNotDeclaredIn(AbstractTask.class)
        .and().areNotAnnotatedWith(Inject.class)
        .should().notHaveRawReturnType(TextResource.class);

    @SuppressWarnings("deprecation")
    @ArchTest
    public static final ArchRule public_api_extension_properties_are_providers = methods()
        .that(are(public_api_methods))
        .and(are(not(declaredIn(assignableTo(Task.class)))))
        .and(are(declaredIn(simpleNameEndingWith("Extension"))))
        .and(are(getters))
        .and().areNotAnnotatedWith(Inject.class)
        .should().haveRawReturnType(assignableTo(Provider.class));
}
