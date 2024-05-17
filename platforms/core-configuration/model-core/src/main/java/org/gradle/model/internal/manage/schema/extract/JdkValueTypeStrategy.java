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

package org.gradle.model.internal.manage.schema.extract;

import org.gradle.model.internal.manage.schema.ScalarValueSchema;
import org.gradle.model.internal.type.ModelType;

public class JdkValueTypeStrategy implements ModelSchemaExtractionStrategy {

    @Override
    public <R> void extract(ModelSchemaExtractionContext<R> extractionContext) {
        ModelType<R> type = extractionContext.getType();
        if (ScalarTypes.isScalarType(type)) {
            extractionContext.found(new ScalarValueSchema<R>(type));
        }
    }
}
