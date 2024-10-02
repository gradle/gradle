/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.execution.model.annotations;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.properties.annotations.AbstractMethodAnnotationHandler;
import org.gradle.internal.properties.annotations.MethodMetadata;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.util.internal.TextUtil;
import org.gradle.work.InputChanges;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.gradle.internal.deprecation.Documentation.userManual;

public class TaskActionAnnotationHandler extends AbstractMethodAnnotationHandler {
    static final String TASKACTION_MUST_HAVE_ZERO_OR_ONE_PARAMETER = "TASKACTION_MUST_HAVE_ZERO_OR_ONE_PARAMETER";

    public TaskActionAnnotationHandler() {
        super(TaskAction.class, ImmutableSet.of());
    }

    @Override
    public boolean isMethodRelevant() {
        return true;
    }

    @Override
    public void validateMethodMetadata(MethodMetadata methodMetadata, TypeValidationContext validationContext) {
        Method method = methodMetadata.getMethod();
        final Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length > 1) {
            validationContext.visitTypeProblem(problem ->
                problem
                    .forMethod(methodMetadata.getMethodName())
                    .id(TextUtil.screamingSnakeToKebabCase(TASKACTION_MUST_HAVE_ZERO_OR_ONE_PARAMETER), "Method has @TaskAction with too many parameters", GradleCoreProblemGroup.validation().type())
                    .contextualLabel(String.format("Method '%s' has @TaskAction annotation with too many method parameters", methodMetadata.getMethodName()))
                    .documentedAt(userManual("validation_problems", TASKACTION_MUST_HAVE_ZERO_OR_ONE_PARAMETER.toLowerCase(Locale.ROOT)))
                    .severity(Severity.ERROR)
                    .details(String.format("A method annotated with @TaskAction must have zero or one parameter of type '%s'", InputChanges.class.getName()))
                    .solution(String.format("Change the method signature of '%s' to have zero parameters", methodMetadata.getMethodName()))
                    .solution(String.format("Change the method signature of '%s' to have one parameter of type '%s'", methodMetadata.getMethodName(), InputChanges.class.getName()))
                    .solution(String.format("Remove the @TaskAction annotation from method '%s'", methodMetadata.getMethodName()))
            );
        } else if (parameterTypes.length == 1 && parameterTypes[0] != InputChanges.class) {
            validationContext.visitTypeProblem(problem ->
                problem
                    .forMethod(methodMetadata.getMethodName())
                    .id(TextUtil.screamingSnakeToKebabCase(TASKACTION_MUST_HAVE_ZERO_OR_ONE_PARAMETER), "Method has @TaskAction with invalid parameter type", GradleCoreProblemGroup.validation().type())
                    .contextualLabel(String.format("Method '%s' has @TaskAction annotation with invalid method parameter type", methodMetadata.getMethodName()))
                    .documentedAt(userManual("validation_problems", TASKACTION_MUST_HAVE_ZERO_OR_ONE_PARAMETER.toLowerCase(Locale.ROOT)))
                    .severity(Severity.ERROR)
                    .details(String.format("A method annotated with @TaskAction must have zero or one parameter of type '%s'", InputChanges.class.getName()))
                    .solution(String.format("Change the method signature of '%s' to have zero parameters", methodMetadata.getMethodName()))
                    .solution(String.format("Change the method signature of '%s' to have one parameter of type '%s'", methodMetadata.getMethodName(), InputChanges.class.getName()))
                    .solution(String.format("Remove the @TaskAction annotation from method '%s'", methodMetadata.getMethodName()))
            );
        }
    }
}
