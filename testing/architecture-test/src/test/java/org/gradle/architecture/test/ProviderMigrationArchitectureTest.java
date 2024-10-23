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
import com.tngtech.archunit.base.HasDescription;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.properties.HasSourceCodeLocation;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.conditions.ArchPredicates;
import org.gradle.StartParameter;
import org.gradle.api.DefaultTask;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.launcher.cli.WelcomeMessageConfiguration;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.options.OptionValues;
import org.gradle.internal.instrumentation.api.annotations.NotToBeMigratedToLazy;
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.model.ModelElement;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.tngtech.archunit.base.DescribedPredicate.and;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaMember.Predicates.declaredIn;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.core.domain.properties.HasReturnType.Predicates.rawReturnType;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.have;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.gradle.architecture.test.ArchUnitFixture.annotatedMaybeInSupertypeWith;
import static org.gradle.architecture.test.ArchUnitFixture.freeze;
import static org.gradle.architecture.test.ArchUnitFixture.public_api_methods;

@AnalyzeClasses(packages = "org.gradle")
public class ProviderMigrationArchitectureTest {
    private static final DescribedPredicate<JavaMethod> getters = new DescribedPredicate<JavaMethod>("getters") {
        @Override
        public boolean test(JavaMethod input) {
            PropertyAccessorType accessorType = PropertyAccessorType.fromName(input.getName());
            if (accessorType == PropertyAccessorType.IS_GETTER) {
                // PropertyAccessorType.IS_GETTER doesn't handle names that start with is
                // but are not getters, e.g. issueManagement is detected as IS_GETTER
                return !Character.isLowerCase(input.getName().charAt(2));
            }
            return accessorType == PropertyAccessorType.GET_GETTER;
        }
    };

    private static final DescribedPredicate<JavaClass> class_with_any_mutable_property = new DescribedPredicate<JavaClass>("class with any mutable property") {
        @Override
        public boolean test(JavaClass input) {
            return input.getAllMethods().stream()
                .filter(getters)
                .anyMatch(ProviderMigrationArchitectureTest::hasSetter);
        }
    };

    private static final DescribedPredicate<JavaMethod> mutable_public_API_properties = ArchPredicates.<JavaMethod>are(public_api_methods)
        .and(not(declaredIn(assignableTo(Task.class))))
        .and(not(declaredIn(StartParameter.class)))
        .and(not(declaredIn(WelcomeMessageConfiguration.class))) // used in StartParameter
        .and(not(declaredIn(Configuration.class)))
        .and(not(declaredIn(FileCollection.class)))
        .and(not(declaredIn(ConfigurableFileCollection.class)))
        .and(are(declaredIn(class_with_any_mutable_property)))
        .and(are(getters))
        .and(not(annotatedWith(Inject.class)))
        .as("mutable public API properties");

    @SuppressWarnings({"deprecation", "UnnecessaryFullyQualifiedName"})
    private static final DescribedPredicate<JavaMethod> task_properties = ArchPredicates.<JavaMethod>are(public_api_methods)
        .and(declaredIn(assignableTo(Task.class)))
        .and(are(getters))
        .and(not(annotatedWith(Inject.class)))
        .and(not(annotatedWith(OptionValues.class)))
        .and(not(declaredIn(Task.class)))
        .and(not(declaredIn(DefaultTask.class)))
        .and(not(declaredIn(org.gradle.api.internal.AbstractTask.class)))
        .as("task properties");

    @ArchTest
    public static final ArchRule mutable_public_api_properties_should_be_providers = freeze(methods()
        .that(are(mutable_public_API_properties))
        .and().doNotHaveRawReturnType(TextResource.class)
        .and().doNotHaveRawReturnType(assignableTo(FileCollection.class))
        .should(haveProviderReturnType()));

    @ArchTest
    public static final ArchRule mutable_public_api_properties_should_be_configurable_file_collections = freeze(methods()
        .that(are(mutable_public_API_properties))
        .and().haveRawReturnType(assignableTo(FileCollection.class))
        .should(haveFileCollectionReturnType()));

    @ArchTest
    public static final ArchRule mutable_public_api_properties_should_not_use_text_resources = freeze(methods()
        .that(are(mutable_public_API_properties))
        .should().notHaveRawReturnType(TextResource.class));

    @ArchTest
    public static final ArchRule public_api_task_properties_are_providers = freeze(methods()
        .that(are(task_properties))
        .and().doNotHaveRawReturnType(TextResource.class)
        .and().doNotHaveRawReturnType(assignableTo(FileCollection.class))
        .should(haveProviderReturnType()));

    @ArchTest
    public static final ArchRule public_api_task_file_properties_are_configurable_file_collections = freeze(methods()
        .that(are(task_properties))
        .and().haveRawReturnType(assignableTo(FileCollection.class))
        .should(haveFileCollectionReturnType()));

    @ArchTest
    public static final ArchRule public_api_task_properties_should_not_use_text_resources = freeze(methods()
        .that(are(task_properties))
        .should().notHaveRawReturnType(TextResource.class));

    private static final DescribedPredicate<JavaMethod> predicate_for_methods_that_should_have_migration_annotation = are(mutable_public_API_properties.or(task_properties))
        // We won't upgrade deprecated methods and classes
        .and(not(annotatedWith(Deprecated.class)))
        .and(not(declaredIn(annotatedWith(Deprecated.class))))
        .and(not(annotatedWith(OptionValues.class)))
        // Skip types that are not to be migrated
        .and(not(declaredIn(annotatedWith(NotToBeMigratedToLazy.class))))
        // Skip Nested properties that are not Iterables
        .and(not(and(
            annotatedMaybeInSupertypeWith(Nested.class),
            not(have(rawReturnType(List.class)))
        )))
        // A lazy type
        .and(not(declaredIn(ConfigurableFileTree.class)))
        // Exceptions should not be upgraded
        .and(not(declaredIn(assignableTo(Exception.class))))
        // Dependency management
        .and(not(declaredIn(resideInAPackage("org.gradle.api.artifacts.."))))
        .and(not(declaredIn(resideInAPackage("org.gradle.api.attributes"))))
        // Software model
        .and(not(declaredIn(assignableTo(ModelElement.class))))
        // IDE Plugins
        .and(not(declaredIn(resideInAPackage("org.gradle.ide.."))))
        .and(not(declaredIn(resideInAPackage("org.gradle.plugins.ide.."))))
        .and(not(declaredIn(resideInAPackage("org.gradle.tooling.."))))
        // Kotlin DSL
        .and(not(declaredIn(resideInAPackage("org.gradle.kotlin.dsl"))))
        // Native
        .and(not(declaredIn(resideInAPackage("org.gradle.nativeplatform.."))))
        .and(not(declaredIn(resideInAPackage("org.gradle.language.nativeplatform.."))))
        .and(not(declaredIn(resideInAPackage("org.gradle.language.swift.."))))
        .and(not(declaredIn(resideInAPackage("org.gradle.language.rc.."))))
        .and(not(declaredIn(resideInAPackage("org.gradle.language.assembler.."))))
        .and(not(have(rawReturnType(assignableTo(Provider.class)))))
        .and(not(have(rawReturnType(assignableTo(DomainObjectSet.class)))))
        .and(not(have(rawReturnType(assignableTo(Project.class)))))
        .and(not(have(rawReturnType(assignableTo(TaskDependency.class)))))
        .and(not(have(rawReturnType(assignableTo(ConfigurableFileCollection.class)))));

    /**
     * Checks that public api properties have {@link ToBeReplacedByLazyProperty} or {@link NotToBeReplacedByLazyProperty} annotation.
     */
    @ArchTest
    public static final ArchRule public_api_properties_should_have_migration_annotation = freeze(methods()
        .that(predicate_for_methods_that_should_have_migration_annotation)
        .should().beAnnotatedWith(ToBeReplacedByLazyProperty.class)
        .orShould().beAnnotatedWith(NotToBeReplacedByLazyProperty.class));

    /**
     * A reverse of {@link #public_api_properties_should_have_migration_annotation}, so we know what types we annotated additionally or accidentally.
     */
    @ArchTest
    public static final ArchRule public_api_properties_should_not_have_migration_annotation = freeze(methods()
        .that(not(predicate_for_methods_that_should_have_migration_annotation))
        .should().notBeAnnotatedWith(ToBeReplacedByLazyProperty.class)
        .andShould().notBeAnnotatedWith(NotToBeReplacedByLazyProperty.class));

    private static HaveLazyReturnType haveProviderReturnType() {
        return new HaveLazyReturnType(Arrays.asList(Property.class, MapProperty.class, ListProperty.class, SetProperty.class), Collections.singletonList(Provider.class));
    }

    private static HaveLazyReturnType haveFileCollectionReturnType() {
        return new HaveLazyReturnType(Collections.singletonList(ConfigurableFileCollection.class), Collections.singletonList(FileCollection.class));
    }

    public static class HaveLazyReturnType extends ArchCondition<JavaMethod> {
        private final List<Class<?>> mutableTypes;
        private final List<Class<?>> immutableTypes;

        public HaveLazyReturnType(List<Class<?>> mutableTypes, List<Class<?>> immutableTypes) {
            super("have return type " + immutableTypes.get(0).getSimpleName());
            this.mutableTypes = mutableTypes;
            this.immutableTypes = immutableTypes;
        }

        @Override
        public void check(JavaMethod javaMethod, ConditionEvents events) {
            boolean hasSetter = hasSetter(javaMethod);
            List<Class<?>> expectedReturnTypes = hasSetter ? mutableTypes : immutableTypes;
            JavaClass returnType = javaMethod.getRawReturnType();
            boolean satisfied = expectedReturnTypes.stream().anyMatch(returnType::isAssignableTo);
            String message = createMessage(javaMethod, (satisfied ? "has " : "does not have ") + "raw return type (" + returnType.getName() + ") assignable to any of " + expectedReturnTypes.stream().map(Class::getSimpleName).collect(Collectors.toList()));
            events.add(new SimpleConditionEvent(javaMethod, satisfied, message));
        }

        private static <T extends HasDescription & HasSourceCodeLocation> String createMessage(T object, String message) {
            return object.getDescription() + " " + message + " in " + object.getSourceCodeLocation();
        }
    }

    private static boolean hasSetter(JavaMethod input) {
        PropertyAccessorType accessorType = PropertyAccessorType.fromName(input.getName());
        String propertyNameFromGetter = accessorType.propertyNameFor(input.getName());
        return input.getOwner().getAllMethods().stream()
            .filter(method -> PropertyAccessorType.fromName(method.getName()) == PropertyAccessorType.SETTER)
            .anyMatch(method -> PropertyAccessorType.SETTER.propertyNameFor(method.getName()).equals(propertyNameFromGetter));
    }
}
