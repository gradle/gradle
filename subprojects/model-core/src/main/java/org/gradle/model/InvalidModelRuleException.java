/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model;

import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

/**
 * Thrown when there is a problem with the usage of a model rule.
 * <p>
 * This exception is different to {@link InvalidModelRuleDeclarationException} in that it signifies a problem
 * with using a model rule in a particular context, whereas {@code InvalidModelRuleDeclarationException} signifies
 * a problem with the declaration of the model rule itself (which therefore means that the rule could not be used in any context).
 * <p>
 * This exception should always have cause, that provides information about the actual problem.
 */
@Incubating
@Contextual
public class InvalidModelRuleException extends GradleException {

    // The usage pattern of this exception providing the rule identity and the cause providing the detail is the
    // way it is due to how we render chained exceptions on build failures.
    // That is, because the information is usually dense, splitting things up this way provides better output.

    private final String descriptor;

    public InvalidModelRuleException(ModelRuleDescriptor descriptor, Throwable cause) {
        super("There is a problem with model rule " + descriptor.toString() + ".", cause);
        if (cause == null) {
            throw new IllegalArgumentException("'cause' cannot be null");
        }
        this.descriptor = descriptor.toString();
    }

    public String getDescriptor() {
        return descriptor;
    }
}
