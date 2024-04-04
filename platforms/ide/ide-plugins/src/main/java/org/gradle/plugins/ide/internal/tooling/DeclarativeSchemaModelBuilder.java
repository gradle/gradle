/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling;

import org.gradle.api.Project;
import org.gradle.internal.declarativedsl.evaluator.DeclarativeSchemaRegistry;
import org.gradle.internal.declarativedsl.toolingapi.DefaultDeclarativeSchema;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.util.Map;

public class DeclarativeSchemaModelBuilder implements ToolingModelBuilder {

    // TODO: relocate to a new "declarative-dsl-tooling-builders" subproject?

    private final DeclarativeSchemaRegistry schemaRegistry;

    public DeclarativeSchemaModelBuilder(DeclarativeSchemaRegistry schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.declarative.dsl.tooling.models.DeclarativeSchemaModel");
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        Map<String, Map<String, String>> schemas = schemaRegistry.serializedSchemas();
        return new DefaultDeclarativeSchema(schemas);
    }

}


