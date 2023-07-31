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

package org.gradle.api.internal.tasks.properties;

import com.google.common.base.Suppliers;
import org.gradle.api.problems.interfaces.ProblemGroup;
import org.gradle.api.provider.Provider;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.util.internal.DeferredUtil;

import javax.annotation.Nullable;
import java.util.function.Supplier;

import static org.gradle.internal.deprecation.Documentation.userManual;

public abstract class AbstractValidatingProperty implements ValidatingProperty {
    private final String propertyName;
    private final PropertyValue value;
    private final boolean optional;
    private final ValidationAction validationAction;

    public AbstractValidatingProperty(String propertyName, PropertyValue value, boolean optional, ValidationAction validationAction) {
        this.propertyName = propertyName;
        this.value = value;
        this.optional = optional;
        this.validationAction = validationAction;
    }

    public static void reportValueNotSet(String propertyName, TypeValidationContext context) {
        context.visitPropertyProblem(problem -> {
            problem.forProperty(propertyName)
                .documentedAt(userManual("validation_problems", "value_not_set"))
                .noLocation()
                .severity(org.gradle.api.problems.interfaces.Severity.ERROR)
                .message("doesn't have a configured value")
                .type(ValidationProblemId.VALUE_NOT_SET.name())
                .group(ProblemGroup.TYPE_VALIDATION_ID)
                .description("This property isn't marked as optional and no value has been configured")
                .solution("Assign a value to '" + propertyName + "'")
                .solution("Mark property '" + propertyName + "' as optional");
        });
    }

    @Override
    public void validate(PropertyValidationContext context) {
        // unnest callables without resolving deferred values (providers, factories)
        Object unnested = DeferredUtil.unpackNestableDeferred(value.call());
        if (isPresent(unnested)) {
            // only resolve deferred values if actually required by some action
            Supplier<Object> valueSupplier = Suppliers.memoize(() -> DeferredUtil.unpack(unnested));
            validationAction.validate(propertyName, valueSupplier, context);
        } else {
            if (!optional) {
                reportValueNotSet(propertyName, context);
            }
        }
    }

    private static boolean isPresent(@Nullable Object value) {
        if (value instanceof Provider) {
            // carefully check for presence without necessarily resolving
            return ((Provider<?>) value).isPresent();
        }
        return value != null;
    }

    @Override
    public void prepareValue() {
        value.maybeFinalizeValue();
    }

    @Override
    public void cleanupValue() {
    }
}
