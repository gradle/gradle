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

package org.gradle.architecture.test;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.gradle.api.internal.provider.AbstractProperty;
import org.gradle.api.provider.Property;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.implement;
import static com.tngtech.archunit.lang.conditions.ArchConditions.beAssignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "org.gradle")
public class PropertyImplementationTest {
    @ArchTest
    public static final ArchRule property_implementations_extend_abstract_property = classes().that(implement(Property.class))
        .should(beAssignableTo(AbstractProperty.class));
}
