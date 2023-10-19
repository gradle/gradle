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
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import groovy.lang.Closure;
import groovy.util.Node;
import groovy.xml.MarkupBuilder;
import kotlin.Pair;
import kotlin.jvm.functions.Function1;
import kotlin.reflect.KClass;
import kotlin.reflect.KProperty;
import org.gradle.api.Plugin;
import org.gradle.api.Task;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.function.BiFunction;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.lang.conditions.ArchConditions.not;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.gradle.architecture.test.ArchUnitFixture.beAbstract;
import static org.gradle.architecture.test.ArchUnitFixture.freeze;
import static org.gradle.architecture.test.ArchUnitFixture.gradleInternalApi;
import static org.gradle.architecture.test.ArchUnitFixture.gradlePublicApi;
import static org.gradle.architecture.test.ArchUnitFixture.haveDirectSuperclassOrInterfaceThatAre;
import static org.gradle.architecture.test.ArchUnitFixture.haveOnlyArgumentsOrReturnTypesThatAre;
import static org.gradle.architecture.test.ArchUnitFixture.not_written_in_kotlin;
import static org.gradle.architecture.test.ArchUnitFixture.primitive;
import static org.gradle.architecture.test.ArchUnitFixture.public_api_methods;
import static org.gradle.architecture.test.ArchUnitFixture.useJavaxAnnotationNullable;

@AnalyzeClasses(packages = "org.gradle")
public class PublicApiCorrectnessTest {

    private static final DescribedPredicate<JavaClass> allowed_types_for_public_api =
        gradlePublicApi()
            .or(primitive)
            // NOTE: we don't want to include java.util.function here because Gradle public API uses custom types like org.gradle.api.Action and org.gradle.api.Spec
            // Mixing these custom types with java.util.function types would make the public API harder to use, especially for plugin authors.
            .or(resideInAnyPackage("java.lang", "java.util", "java.util.concurrent", "java.util.regex", "java.lang.reflect", "java.io")
                .or(type(byte[].class))
                .or(type(URI.class))
                .or(type(URL.class))
                .or(type(Duration.class))
                .or(type(BigDecimal.class))
                .or(type(Element.class))
                .or(type(QName.class))
                .or(type(BiFunction.class))
                .as("built-in JDK classes"))
            .or(type(Node.class)
                .or(type(MarkupBuilder.class))
                .or(type(Closure.class))
                .as("Groovy classes")
            )
            .or(type(Function1.class)
                .or(type(KClass.class))
                .or(type(KClass[].class))
                .or(type(KProperty.class))
                .or(type(Pair[].class))
                .as("Kotlin classes")
            );
    private static final DescribedPredicate<JavaClass> public_api_tasks_or_plugins =
            gradlePublicApi().and(assignableTo(Task.class).or(assignableTo(Plugin.class)));

    @ArchTest
    public static final ArchRule public_api_methods_do_not_reference_internal_types_as_parameters = freeze(methods()
        .that(are(public_api_methods))
        .should(haveOnlyArgumentsOrReturnTypesThatAre(allowed_types_for_public_api))
    );

    @ArchTest
    public static final ArchRule public_api_tasks_and_plugins_are_abstract = classes()
            .that(are(public_api_tasks_or_plugins))
            .should(beAbstract());

    @ArchTest
    public static final ArchRule public_api_classes_do_not_extend_internal_types = freeze(classes()
        .that(are(gradlePublicApi()))
        .should(not(haveDirectSuperclassOrInterfaceThatAre(gradleInternalApi())))
    );

    /**
     * Code written in Kotlin implicitly uses {@link org.jetbrains.annotations.Nullable}, so
     * those packages are excluded from this check.
     */
    @ArchTest
    public static final ArchRule all_methods_use_proper_Nullable = methods()
            .that(are(not_written_in_kotlin))
            .should(useJavaxAnnotationNullable()
    );
}
