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

package org.gradle.api.internal.tasks.properties;

import org.gradle.api.internal.provider.ProducerAware;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.util.internal.DeferredUtil;
import org.gradle.util.internal.TextUtil;

import java.util.Locale;

import static org.gradle.internal.deprecation.Documentation.userManual;

/**
 * Warns when an output property is a plain {@link org.gradle.api.provider.Provider} that does not carry its producing task.
 * Consumers of such a provider do not get an implicit dependency on the task.
 *
 * <p>Gradle decorates the value returned from an overridable output getter so that it carries the task (see
 * {@code OutputPropertyRoleAnnotationHandler}). The value of a final getter, e.g. a Kotlin {@code val}, or of a Groovy
 * field cannot be decorated and ends up here.</p>
 */
public class OutputProviderWithoutProducerValidatingProperty implements ValidatingProperty {
    private static final String OUTPUT_PROVIDER_WITHOUT_PRODUCER = "OUTPUT_PROVIDER_WITHOUT_PRODUCER";

    private final String propertyName;
    private final PropertyValue value;

    public OutputProviderWithoutProducerValidatingProperty(String propertyName, PropertyValue value) {
        this.propertyName = propertyName;
        this.value = value;
    }

    @Override
    public void validate(PropertyValidationContext context) {
        Object unnested = DeferredUtil.unpackNestableDeferred(value.call());
        if (unnested instanceof ProviderInternal && !(unnested instanceof ProducerAware)) {
            context.visitPropertyWarning(problem ->
                problem
                    .forProperty(propertyName)
                    .id(TextUtil.screamingSnakeToKebabCase(OUTPUT_PROVIDER_WITHOUT_PRODUCER), "Output provider is not associated with its producing task", GradleCoreProblemGroup.validation().property())
                    .contextualLabel("is a Provider that is not associated with this task")
                    .documentedAt(userManual("validation_problems", OUTPUT_PROVIDER_WITHOUT_PRODUCER.toLowerCase(Locale.ROOT)))
                    .details("The property is declared as an output, but the provider does not know that this task produces its value, so other tasks consuming this provider do not get an implicit dependency on this task. Gradle can only associate the value with the task when it is returned from a non-final getter.")
                    .solution("Declare the property with a non-final getter, e.g. mark the property 'open' in Kotlin or declare an explicit getter method in Groovy")
                    .solution("Declare the property with a Property type such as RegularFileProperty, DirectoryProperty or ConfigurableFileCollection instead")
            );
        }
    }

    @Override
    public void prepareValue() {
    }

    @Override
    public void cleanupValue() {
    }
}
