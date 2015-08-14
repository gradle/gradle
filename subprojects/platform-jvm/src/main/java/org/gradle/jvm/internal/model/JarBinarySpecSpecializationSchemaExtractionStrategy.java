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
import org.gradle.model.internal.manage.schema.*;
import org.gradle.model.internal.manage.schema.extract.*;
import org.gradle.model.internal.type.ModelType;

import javax.inject.Inject;
import java.util.List;

public class JarBinarySpecSpecializationSchemaExtractionStrategy extends ManagedImplStructSchemaExtractionStrategySupport {

    private final ManagedProxyClassGenerator classGenerator = new ManagedProxyClassGenerator();

    @Inject
    public JarBinarySpecSpecializationSchemaExtractionStrategy(ModelSchemaAspectExtractor aspectExtractor) {
        super(aspectExtractor);
    }

    @Override
    protected boolean isTarget(ModelType<?> type) {
        return super.isTarget(type)
            && !type.getRawClass().equals(JarBinarySpec.class)
            && JarBinarySpec.class.isAssignableFrom(type.getRawClass());
    }

    @Override
    protected <R> ModelSchema<R> createSchema(final ModelSchemaExtractionContext<R> extractionContext, final ModelSchemaStore store, ModelType<R> type, List<ModelProperty<?>> properties, List<ModelSchemaAspect> aspects) {
        Class<? extends R> implClass = classGenerator.generate(type.getConcreteClass(), JarBinarySpecInternal.class, properties);
        return new ModelManagedImplStructSchema<R>(type, properties, aspects, implClass, JarBinarySpecInternal.class, new Function<ModelManagedImplStructSchema<R>, NodeInitializer>() {
            @Override
            public NodeInitializer apply(ModelManagedImplStructSchema<R> schema) {
                return new JarBinarySpecSpecializationModelInitializer<R>(schema, store);
            }
        });
    }
}
