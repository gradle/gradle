/*
 * Copyright 2026 the original author or authors.
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
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.provider.support.BackedByProperty;
import org.gradle.api.provider.HasMultipleValues;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

@AnalyzeClasses(packages = "org.gradle")
public class BackedByPropertyArchitectureTest {

    private static final List<Class<?>> LAZY_RETURN_TYPES = Arrays.asList(
        Property.class,
        MapProperty.class,
        HasMultipleValues.class, // ListProperty / SetProperty
        DirectoryProperty.class,
        RegularFileProperty.class,
        ConfigurableFileCollection.class
    );

    @ArchTest
    public static final ArchRule backed_by_property_targets_a_lazy_getter_on_the_same_class = methods()
        .that().areAnnotatedWith(BackedByProperty.class)
        .should(targetLazyGetterOnSameClass());

    @ArchTest
    public static final ArchRule backed_by_property_getter_forwarders_on_tasks_are_internal = methods()
        .that().areAnnotatedWith(BackedByProperty.class)
        .and().haveRawParameterTypes(new Class<?>[0])
        .and().areDeclaredInClassesThat().areAssignableTo(Task.class)
        .should().beAnnotatedWith(Internal.class);

    private static ArchCondition<JavaMethod> targetLazyGetterOnSameClass() {
        return new ArchCondition<JavaMethod>("target a lazy-property getter declared on the same class") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                String canonicalGetterName = method.reflect().getAnnotation(BackedByProperty.class).value();
                JavaClass owner = method.getOwner();
                Optional<JavaMethod> canonical = owner.getAllMethods().stream()
                    .filter(m -> m.getName().equals(canonicalGetterName))
                    .filter(m -> m.getRawParameterTypes().isEmpty())
                    .findFirst();
                if (!canonical.isPresent()) {
                    events.add(SimpleConditionEvent.violated(method,
                        describe(method) + " is @BackedByProperty(\"" + canonicalGetterName + "\") but no zero-arg getter with that name exists on " + owner.getName()));
                    return;
                }
                JavaClass returnType = canonical.get().getRawReturnType();
                boolean returnsLazy = LAZY_RETURN_TYPES.stream().anyMatch(returnType::isAssignableTo);
                if (!returnsLazy) {
                    events.add(SimpleConditionEvent.violated(method,
                        describe(method) + " is @BackedByProperty(\"" + canonicalGetterName + "\") but " + canonicalGetterName + " returns " + returnType.getName() + " which is not a lazy-property type"));
                }
            }

            private String describe(JavaMethod method) {
                return method.getOwner().getName() + "." + method.getName() + "(...)";
            }
        };
    }
}
