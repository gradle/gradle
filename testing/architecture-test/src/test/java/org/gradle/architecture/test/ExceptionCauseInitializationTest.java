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
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.gradle.api.GradleException;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;

/**
 * Validates that a {@link GradleException} subclass whose only constructor argument is a message does not route
 * that constructor through one that takes a cause.
 * <p>
 * {@link Throwable#initCause(Throwable)} refuses to run twice, and it decides whether the cause has already been
 * set by comparing the field against the throwable itself rather than against {@code null}. The constructors that
 * take no cause leave that sentinel in place; {@link Throwable#Throwable(String, Throwable)} overwrites it
 * unconditionally. So {@code super(message, null)} and {@code super(message)} produce the same
 * {@link Throwable#getCause()} but opposite {@code initCause} behaviour - after the former, every later
 * {@code initCause} call throws {@link IllegalStateException}.
 * <p>
 * The consequences are easy to miss, because nothing fails loudly:
 * <ul>
 *     <li>{@code new SomeGradleException(message).initCause(failure)} starts throwing, in Gradle's own code and in
 *     third-party subclasses.</li>
 *     <li>Exceptions stop surviving a trip between processes with their own type. When Java deserialization fails,
 *     {@code ExceptionPlaceholder} rebuilds an exception by calling its single-message constructor and then
 *     {@code initCause(...)}; that call now throws, the failure is swallowed as a debug log, and the exception
 *     arrives as a {@code PlaceholderException} without its real type or its resolutions.</li>
 * </ul>
 */
@AnalyzeClasses(packages = "org.gradle")
public class ExceptionCauseInitializationTest {

    @ArchTest
    public static final ArchRule message_only_constructors_do_not_initialize_the_cause =
        constructors()
            .that().areDeclaredInClassesThat().areAssignableTo(GradleException.class)
            .and().haveRawParameterTypes(String.class)
            .should(notChainToAConstructorThatTakesACause())
            .because("initializing the cause - even to null - makes any later initCause(...) call throw, "
                + "which breaks the common new SomeException(message).initCause(failure) pattern and stops "
                + "ExceptionPlaceholder reconstructing the exception with its real type");

    private static ArchCondition<JavaConstructor> notChainToAConstructorThatTakesACause() {
        return new ArchCondition<JavaConstructor>("not chain to a constructor that takes a cause") {
            @Override
            public void check(JavaConstructor constructor, ConditionEvents events) {
                for (JavaConstructorCall call : constructor.getConstructorCallsFromSelf()) {
                    if (isConstructorChainCall(constructor, call) && takesACause(call.getTarget().getRawParameterTypes())) {
                        events.add(SimpleConditionEvent.violated(constructor, String.format(
                            "%s chains to %s, which initializes the cause, in %s",
                            constructor.getFullName(),
                            call.getTarget().getFullName(),
                            call.getSourceCodeLocation()
                        )));
                    }
                }
            }
        };
    }

    /**
     * Distinguishes a {@code this(...)} or {@code super(...)} call from an unrelated {@code new Something(...)}:
     * only the former targets the declaring class itself or one of its supertypes.
     */
    private static boolean isConstructorChainCall(JavaConstructor constructor, JavaConstructorCall call) {
        return constructor.getOwner().isAssignableTo(call.getTarget().getOwner().getName());
    }

    private static boolean takesACause(List<JavaClass> parameterTypes) {
        return parameterTypes.stream().anyMatch(parameterType -> parameterType.isAssignableTo(Throwable.class));
    }
}
