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

import org.gradle.api.Incubating;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

/**
 * Thrown when at attempt is made to mutate a subject of a rule after the rule has completed.
 * <p>
 * This can potentially happen when a reference to the subject is retained during a rule and then used afterwards,
 * Such as when an anonymous inner class or closure “closes over” the subject.
 */
@Incubating
public class ModelViewClosedException extends ReadOnlyModelViewException {
    public ModelViewClosedException(ModelPath path, ModelType<?> type, ModelRuleDescriptor ruleDescriptor) {
        super(createMessage("closed", path, type, ruleDescriptor));
    }
}
