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
import org.gradle.api.problems.ProblemSpec;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.provider.HasConfigurableValue;
import org.gradle.api.provider.Provider;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.util.internal.DeferredUtil;
import org.gradle.util.internal.TextUtil;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
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

    private static final String VALUE_NOT_SET = "VALUE_NOT_SET";

    public static void reportValueNotSet(String propertyName, TypeValidationContext context, boolean hasConfigurableValue) {
        context.visitPropertyProblem(problem -> {
            ProblemSpec problemSpec = problem.forProperty(propertyName)
                .id(TextUtil.screamingSnakeToKebabCase(VALUE_NOT_SET), "Value not set", GradleCoreProblemGroup.validation().property())
                .contextualLabel("doesn't have a configured value")
                .documentedAt(userManual("validation_problems", VALUE_NOT_SET.toLowerCase(Locale.ROOT)))
                .severity(Severity.ERROR)
                .details("This property isn't marked as optional and no value has been configured");
            if (hasConfigurableValue) {
                problemSpec.solution("Assign a value to '" + propertyName + "'");
            } else {
                problemSpec.solution("The value of '" + propertyName + "' is calculated, make sure a valid value can be calculated");
            }
            problemSpec.solution("Mark property '" + propertyName + "' as optional");
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
                reportValueNotSet(propertyName, context, hasConfigurableValue(unnested));
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

    private static boolean hasConfigurableValue(@Nullable Object value) {
        // TODO We should check the type of the property here, not its value
        //   With the current code we'd assume a `Provider<String>` to be configurable when
        //   the getter returns `null`. The property type is not currently available in this
        //   context, though.
        return value == null || HasConfigurableValue.class.isAssignableFrom(value.getClass());
    }

    @Override
    public void prepareValue() {
        value.maybeFinalizeValue();
    }

    @Override
    public void cleanupValue() {
    }
}
