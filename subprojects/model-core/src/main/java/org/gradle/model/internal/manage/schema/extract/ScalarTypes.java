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
package org.gradle.model.internal.manage.schema.extract;

import com.google.common.collect.ImmutableList;
import org.gradle.model.internal.type.ModelType;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

public abstract class ScalarTypes {

    public final static List<ModelType<?>> TYPES = ImmutableList.<ModelType<?>>of(
        ModelType.of(String.class),
        ModelType.of(Boolean.class),
        ModelType.of(Character.class),
        ModelType.of(Byte.class),
        ModelType.of(Short.class),
        ModelType.of(Integer.class),
        ModelType.of(Float.class),
        ModelType.of(Long.class),
        ModelType.of(Double.class),
        ModelType.of(BigInteger.class),
        ModelType.of(BigDecimal.class),
        ModelType.of(File.class)
    );

    public static boolean isScalarType(ModelType<?> modelType) {
        return TYPES.contains(modelType);
    }
}
