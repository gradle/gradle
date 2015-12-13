/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.inspect;

import org.gradle.model.internal.core.ExtractedModelRule;
import org.gradle.model.internal.manage.schema.AbstractModelSchema;
import org.gradle.model.internal.type.ModelType;

import java.util.List;

/**
 * The schema for a {@link org.gradle.model.RuleSource}.
 */
public class RuleSourceSchema<T> extends AbstractModelSchema<T> {
    private final List<ExtractedModelRule> rules;

    public RuleSourceSchema(ModelType<T> type, List<ExtractedModelRule> rules) {
        super(type);
        this.rules = rules;
    }

    public List<ExtractedModelRule> getRules() {
        return rules;
    }
}
