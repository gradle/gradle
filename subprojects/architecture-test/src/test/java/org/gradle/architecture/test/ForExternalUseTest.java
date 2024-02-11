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

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.gradle.internal.accesscontrol.AllowUsingApiForExternalUse;
import org.gradle.internal.accesscontrol.ForExternalUse;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchConditions.beDeclaredInClassesThat;
import static com.tngtech.archunit.lang.conditions.ArchConditions.beProtected;
import static com.tngtech.archunit.lang.conditions.ArchConditions.bePublic;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static org.gradle.architecture.test.ArchUnitFixture.annotatedMaybeInSupertypeWith;
import static org.gradle.architecture.test.ArchUnitFixture.gradlePublicApi;

@AnalyzeClasses(packages = "org.gradle")
public class ForExternalUseTest {

    @ArchTest
    public static final ArchRule members_for_external_use_are_public_api =
        codeUnits().that().areAnnotatedWith(ForExternalUse.class)
            .should(beDeclaredInClassesThat(are(gradlePublicApi())))
            .andShould(bePublic().or(beProtected()));

    @ArchTest
    public static final ArchRule members_for_external_use_are_not_used_internally =
        codeUnits().that(are(annotatedMaybeInSupertypeWith(ForExternalUse.class)))
            .should().onlyBeCalled().byCodeUnitsThat(are(
                annotatedMaybeInSupertypeWith(ForExternalUse.class)
                    .or(annotatedWith(AllowUsingApiForExternalUse.class))
            ));
}
