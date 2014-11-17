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

import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.Factory;
import org.gradle.model.Managed;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;

import java.util.Collections;

@ThreadSafe
public class StructStrategy extends StructStrategySupport {

    public StructStrategy(ModelSchemaExtractor extractor, Factory<String> supportedTypeDescriptions) {
        super(extractor, supportedTypeDescriptions);
    }

    public Iterable<String> getSupportedManagedTypes() {
        return Collections.singleton("interfaces annotated with " + Managed.class.getName());
    }

    @Override
    protected <R> ModelSchema<R> createModelSchema(ModelType<R> type, Iterable<ModelProperty<?>> declaredProperties, Iterable<ModelProperty<?>> inheritedProperties) {
        return ModelSchema.struct(type, declaredProperties, inheritedProperties);
    }

    @Override
    protected <R> boolean handlesType(ModelType<R> type) {
        return type.getRawClass().isAnnotationPresent(Managed.class);
    }

}
