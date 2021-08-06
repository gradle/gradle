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

import com.google.common.collect.ImmutableSet;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClassList;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import groovy.lang.Closure;
import groovy.util.Node;
import groovy.xml.MarkupBuilder;
import org.w3c.dom.Element;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.stream.Stream;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.core.domain.JavaMember.Predicates.declaredIn;
import static com.tngtech.archunit.core.domain.JavaModifier.PUBLIC;
import static com.tngtech.archunit.core.domain.properties.HasModifiers.Predicates.modifier;
import static com.tngtech.archunit.lang.conditions.ArchConditions.not;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.gradle.architecture.test.ArchUnitFixture.freeze;
import static org.gradle.architecture.test.ArchUnitFixture.gradleInternalApi;
import static org.gradle.architecture.test.ArchUnitFixture.gradlePublicApi;
import static org.gradle.architecture.test.ArchUnitFixture.haveDirectSuperclassOrInterfaceThatAre;

@AnalyzeClasses(packages = "org.gradle")
public class PublicApiAccessTest {

    private static final DescribedPredicate<JavaMember> public_api_methods = declaredIn(gradlePublicApi())
        .and(modifier(PUBLIC))
        .as("public API methods");

    private static final DescribedPredicate<JavaClass> primitive = new DescribedPredicate<JavaClass>("primitive") {
        @Override
        public boolean apply(JavaClass input) {
            return input.isPrimitive();
        }
    };

    private static final DescribedPredicate<JavaClass> allowed_types_for_public_api =
        gradlePublicApi()
            .or(primitive)
            .or(resideInAnyPackage("java.lang", "java.util", "java.util.concurrent", "java.util.regex", "java.util.function", "java.lang.reflect", "java.io")
                .or(type(File[].class))
                .or(type(FileInputStream.class))
                .or(type(Reader.class))
                .or(type(OutputStream.class))
                .or(type(URI.class))
                .or(type(URL.class))
                .or(type(InputStream.class))
                .or(type(Element.class))
                .or(type(StringWriter.class))
                .or(type(Writer.class))
                .or(type(Duration.class))
                .or(type(byte[].class))
                .or(type(BigDecimal.class))
                .as("built-in JDK classes"))
            .or(type(Node.class)
                .or(type(MarkupBuilder.class))
                .or(type(Closure.class))
                .as("Groovy classes")
            );

    @ArchTest
    public static final ArchRule public_api_methods_do_not_reference_internal_types_as_parameters = freeze(methods()
        .that(are(public_api_methods))
        .should(not(haveArgumentsOrReturnTypesThatAre(gradleInternalApi())))
    );

    @ArchTest
    public static final ArchRule public_api_classes_do_not_extend_internal_types = freeze(classes()
        .that(are(gradlePublicApi()))
        .should(not(haveDirectSuperclassOrInterfaceThatAre(gradleInternalApi())))
    );

    static ArchCondition<JavaMethod> haveArgumentsOrReturnTypesThatAre(DescribedPredicate<JavaClass> types) {
        return new MethodReferences(types);
    }

    static class MethodReferences extends ArchCondition<JavaMethod> {
        private final DescribedPredicate<JavaClass> types;

        public MethodReferences(DescribedPredicate<JavaClass> types) {
            super("have arguments or return types that are %s", types.getDescription());
            this.types = types;
        }

        @Override
        public void check(JavaMethod method, ConditionEvents events) {
            JavaClass rawReturnType = method.getRawReturnType();
            JavaClassList rawParameterTypes = method.getRawParameterTypes();
            ImmutableSet<String> matchedClasses = Stream.concat(Stream.of(rawReturnType), rawParameterTypes.stream())
                .filter(types::apply)
                .map(JavaClass::getName)
                .collect(ImmutableSet.toImmutableSet());
            boolean fulfilled = !matchedClasses.isEmpty();
            String verb = matchedClasses.size() == 1 ? "is" : "are";
            String classesDescription = fulfilled ? String.join(", ", matchedClasses) : "no classes";
            String message = String.format("%s has arguments/return type %s that %s %s in %s",
                method.getDescription(),
                classesDescription,
                verb,
                types.getDescription(),
                method.getSourceCodeLocation()
            );
            events.add(new SimpleConditionEvent(method, fulfilled, message));
        }
    }
}
