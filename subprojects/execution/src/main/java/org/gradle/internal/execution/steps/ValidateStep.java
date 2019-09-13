/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.execution.steps;

import com.google.common.collect.ImmutableList;
import org.gradle.api.InvalidUserDataException;
import org.gradle.internal.execution.Context;
import org.gradle.internal.execution.Result;
import org.gradle.internal.execution.Step;
import org.gradle.internal.reflect.WorkValidationContext;
import org.gradle.internal.reflect.WorkValidationException;

import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Collectors;

public class ValidateStep<C extends Context, R extends Result> implements Step<C, R> {
    private final Step<? super C, ? extends R> delegate;

    public ValidateStep(Step<? super C, ? extends R> delegate) {
        this.delegate = delegate;
    }

    @Override
    public R execute(C context) {
        ValidationContext validationContext = new ValidationContext();
        context.getWork().validate(validationContext);
        ImmutableList<String> messages = validationContext.getMessages();
        if (!messages.isEmpty()) {
            String displayName = context.getWork().getDisplayName();
            String errorMessage = messages.size() == 1
                ? String.format("A problem was found with the configuration of %s.", displayName)
                : String.format("Some problems were found with the configuration of %s.", displayName);
            List<InvalidUserDataException> causes = messages.stream()
                .limit(5)
                .sorted()
                .map(InvalidUserDataException::new)
                .collect(Collectors.toList());
            throw new WorkValidationException(errorMessage, causes);
        }
        return delegate.execute(context);
    }

    private static class ValidationContext implements WorkValidationContext {
        private final ImmutableList.Builder<String> messages = ImmutableList.builder();

        @Override
        public void visitWarning(@Nullable String ownerPath, String propertyName, String message) {
            visitWarning(WorkValidationContext.decorateMessage(ownerPath, propertyName, message));
        }

        @Override
        public void visitWarning(String message) {
            messages.add(message);
        }

        @Override
        public void visitError(@Nullable String ownerPath, String propertyName, String message) {
            visitError(WorkValidationContext.decorateMessage(ownerPath, propertyName, message));
        }

        @Override
        public void visitError(String message) {
            visitWarning(message);
        }

        public ImmutableList<String> getMessages() {
            return messages.build();
        }
    }
}
