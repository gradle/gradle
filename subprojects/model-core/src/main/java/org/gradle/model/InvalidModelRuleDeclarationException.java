/*
 * Copyright 2014 the original author or authors.
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
 * Thrown when a model rule, or source of model rules, is declared in an invalid way.
 */
@Incubating
@Contextual
public class InvalidModelRuleDeclarationException extends GradleException {

    public InvalidModelRuleDeclarationException(String message) {
        super(message);
    }

    public InvalidModelRuleDeclarationException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidModelRuleDeclarationException(ModelRuleDescriptor descriptor, Throwable cause) {
        super("Declaration of model rule " + descriptor.toString() + " in invalid.", cause);
        if (cause == null) {
            throw new IllegalArgumentException("'cause' cannot be null");
        }
    }

}
