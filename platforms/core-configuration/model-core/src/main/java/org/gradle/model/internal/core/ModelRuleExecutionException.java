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

package org.gradle.model.internal.core;

import org.gradle.api.GradleException;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

@Contextual
// TODO should include some context on what the rule was trying to do (create vs. mutate)
public class ModelRuleExecutionException extends GradleException {

    public ModelRuleExecutionException(ModelRuleDescriptor descriptor, Throwable cause) {
        super(toMessage(descriptor), cause);
    }

    public ModelRuleExecutionException(ModelRuleDescriptor descriptor, String error) {
        super(toMessage(descriptor, error));
    }

    private static String toMessage(ModelRuleDescriptor descriptor) {
        StringBuilder builder = new StringBuilder("Exception thrown while executing model rule: ");
        descriptor.describeTo(builder);
        return builder.toString();
    }

    private static String toMessage(ModelRuleDescriptor descriptor, String error) {
        StringBuilder builder = new StringBuilder("error executing model rule: ");
        descriptor.describeTo(builder);
        builder.append(" - ");
        builder.append(error);
        return builder.toString();
    }

}
