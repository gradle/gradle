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

public interface ModelSchemaExtractionStrategy {
    /**
     * Potentially extracts the schema for a type. If this strategy does not recognize the type, this method should return without doing anything.
     *
     * If the strategy does recognize the type, it should call {@link ModelSchemaExtractionContext#found(org.gradle.model.internal.manage.schema.ModelSchema)} with the resulting schema, or one of the
     * methods defined by {@link org.gradle.model.internal.inspect.ValidationProblemCollector} to record problems with the type. The strategy can both provide a result and record problems, if
     * desired.
     */
    <T> void extract(ModelSchemaExtractionContext<T> extractionContext);
}
