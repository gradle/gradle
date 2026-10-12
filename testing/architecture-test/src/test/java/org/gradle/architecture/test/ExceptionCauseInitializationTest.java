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

import com.tngtech.archunit.base.DescribedPredicate;
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
 * Validates that a {@link GradleException} subclass does not initialize its cause unless it was given one.
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
 * <p>
 * A constructor that accepts a cause is free to pass it on - that is the caller's choice, made explicitly. The rule
 * covers the constructors that were given no cause to pass, whatever their other parameters: a
 * {@code (String, Iterable<String>)} constructor handing {@code super(message, null, resolutions)} a null cause is
 * the same defect as a {@code (String)} constructor delegating to {@code this(message, null)}.
 */
@AnalyzeClasses(packages = "org.gradle")
public class ExceptionCauseInitializationTest {

    @ArchTest
    public static final ArchRule constructors_given_no_cause_do_not_initialize_one =
        constructors()
            .that().areDeclaredInClassesThat().areAssignableTo(GradleException.class)
            .and(doNotAcceptACause())
            .should(notChainToAConstructorThatTakesACause())
            .because("initializing the cause - even to null - makes any later initCause(...) call throw, "
                + "which breaks the common new SomeException(message).initCause(failure) pattern and stops "
                + "ExceptionPlaceholder reconstructing the exception with its real type");

    /**
     * Selects the constructors the rule applies to: those handed no cause of any shape, so they have nothing
     * legitimate to pass to a cause-taking constructor.
     * <p>
     * Varargs and array parameters count as accepting a cause, so that a {@code (String, Throwable...)} constructor
     * is left alone. Generic parameters such as {@code Iterable<? extends Throwable>} cannot be recognised here,
     * because the raw parameter type erases to {@code Iterable} and is indistinguishable from an
     * {@code Iterable<String>} of resolutions. Those constructors are therefore covered by the rule, which is
     * correct for the ones in this codebase: they record their causes through {@code initCauses(...)} after calling
     * a cause-free super constructor.
     */
    private static DescribedPredicate<JavaConstructor> doNotAcceptACause() {
        return new DescribedPredicate<JavaConstructor>("do not accept a cause") {
            @Override
            public boolean test(JavaConstructor constructor) {
                return constructor.getRawParameterTypes().stream().noneMatch(ExceptionCauseInitializationTest::isCause);
            }
        };
    }

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

    private static boolean isCause(JavaClass parameterType) {
        if (parameterType.isArray()) {
            return parameterType.getComponentType().isAssignableTo(Throwable.class);
        }
        return parameterType.isAssignableTo(Throwable.class);
    }
}
