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
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.gradle.api.model.ManagedType;

import java.util.Set;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchConditions.be;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Ensures we keep track of all {@link ManagedType manged types} that can be instantiated by Gradle.
 */
@AnalyzeClasses(packages = "org.gradle")
public class SupportedManagedTypesTest {

    /**
     * If this list changes, be sure to also update the relevant documentation.
     *
     * @see <a href="https://docs.gradle.org/current/userguide/properties_providers.html">link</a>
     */
    private static final Set<String> ALLOWED_CLASSES = Set.of(
        org.gradle.api.DomainObjectSet.class.getName(),
        org.gradle.api.ExtensiblePolymorphicDomainObjectContainer.class.getName(),
        org.gradle.api.NamedDomainObjectContainer.class.getName(),
        org.gradle.api.NamedDomainObjectList.class.getName(),
        org.gradle.api.NamedDomainObjectSet.class.getName(),
        org.gradle.api.artifacts.dsl.DependencyCollector.class.getName(),
        org.gradle.api.file.ConfigurableFileCollection.class.getName(),
        org.gradle.api.file.ConfigurableFileTree.class.getName(),
        org.gradle.api.file.DirectoryProperty.class.getName(),
        org.gradle.api.file.RegularFileProperty.class.getName(),
        org.gradle.api.provider.ListProperty.class.getName(),
        org.gradle.api.provider.MapProperty.class.getName(),
        org.gradle.api.provider.Property.class.getName(),
        org.gradle.api.provider.SetProperty.class.getName()
    );

    private static final DescribedPredicate<JavaClass> A_KNOWN_MANAGED_TYPE = new DescribedPredicate<>("a known managed type") {
        @Override
        public boolean test(JavaClass javaClass) {
            return ALLOWED_CLASSES.contains(javaClass.getName());
        }
    };

    @ArchTest
    public static final ArchRule annotated_managed_types_are_known = classes()
        .that(are(annotatedWith(ManagedType.class)))
        .should(be(A_KNOWN_MANAGED_TYPE));

    @ArchTest
    public static final ArchRule known_managed_types_are_annotated = classes()
        .that(are(A_KNOWN_MANAGED_TYPE))
        .should(be(annotatedWith(ManagedType.class)));

}
