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
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

/**
 * Thrown when an attempt is made to read the value of a model element that is not readable at the time.
 */
@Incubating
public class WriteOnlyModelViewException extends GradleException {

    public WriteOnlyModelViewException(String property, ModelPath path, ModelType<?> type, ModelRuleDescriptor ruleDescriptor) {
        super(createMessage(property, path, type, ruleDescriptor));
    }

    private static String createMessage(String property, ModelPath path, ModelType<?> type, ModelRuleDescriptor ruleDescriptor) {
        StringBuilder result = new StringBuilder();
        result.append("Attempt to read");
        if (property != null) {
            result.append(" property '");
            result.append(property);
            result.append("'");
        }
        result.append(" from a write only view of model element '");
        result.append(path);
        result.append("'");
        if (!type.equals(ModelType.UNTYPED)) {
            result.append(" of type '");
            result.append(type.getDisplayName());
            result.append("'");
        }
        result.append(" given to rule ");
        ruleDescriptor.describeTo(result);
        return result.toString();
    }
}
