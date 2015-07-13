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

package org.gradle.jvm.internal.model;

import com.google.common.base.Function;
import org.gradle.jvm.JarBinarySpec;
import org.gradle.jvm.internal.JarBinarySpecInternal;
import org.gradle.model.internal.core.NodeInitializer;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.ModelStructSchema;
import org.gradle.model.internal.manage.schema.extract.ManagedImplTypeSchemaExtractionStrategySupport;
import org.gradle.model.internal.manage.schema.extract.ManagedProxyClassGenerator;
import org.gradle.model.internal.manage.schema.extract.ModelSchemaExtractionContext;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.Method;
import java.util.List;

public class JarBinarySpecSpecializationSchemaExtractionStrategy extends ManagedImplTypeSchemaExtractionStrategySupport {

    private final ManagedProxyClassGenerator classGenerator = new ManagedProxyClassGenerator();

    @Override
    protected boolean isTarget(ModelType<?> type) {
        return super.isTarget(type)
            && !type.getRawClass().equals(JarBinarySpec.class)
            && JarBinarySpec.class.isAssignableFrom(type.getRawClass());
    }

    @Override
    protected boolean ignoreMethod(Method method) {
        try {
            JarBinarySpec.class.getMethod(method.getName(), method.getParameterTypes());
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @Override
    protected <R> ModelSchema<R> createSchema(final ModelSchemaExtractionContext<R> extractionContext, final ModelSchemaStore store, ModelType<R> type, List<ModelProperty<?>> properties, Class<R> concreteClass) {
        Class<? extends R> implClass = classGenerator.generate(concreteClass, JarBinarySpecInternal.class);
        return ModelSchema.struct(type, properties, implClass, JarBinarySpecInternal.class, new Function<ModelStructSchema<R>, NodeInitializer>() {
            @Override
            public NodeInitializer apply(ModelStructSchema<R> schema) {
                return new JarBinarySpecSpecializationModelInitializer<R>(schema, store);
            }
        });
    }
}
