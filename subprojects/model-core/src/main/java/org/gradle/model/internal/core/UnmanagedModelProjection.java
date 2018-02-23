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

package org.gradle.model.internal.core;

import com.google.common.base.Optional;
import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.Cast;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import static org.gradle.internal.reflect.JavaReflectionUtil.hasDefaultToString;
import static org.gradle.model.internal.manage.schema.extract.PrimitiveTypes.defaultValueOf;
import static org.gradle.model.internal.manage.schema.extract.PrimitiveTypes.isPrimitiveType;
import static org.gradle.model.internal.manage.schema.extract.ScalarTypes.isScalarType;

@ThreadSafe
public class UnmanagedModelProjection<M> extends TypeCompatibilityModelProjectionSupport<M> {

    public static <M> ModelProjection of(ModelType<M> type) {
        return new UnmanagedModelProjection<M>(type);
    }

    public static <M> ModelProjection of(Class<M> type) {
        return of(ModelType.of(type));
    }

    public UnmanagedModelProjection(ModelType<M> type) {
        super(type);
    }

    @Override
    protected ModelView<M> toView(MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean writable) {
        M instance = Cast.uncheckedCast(modelNode.getPrivateData());
        return InstanceModelView.of(modelNode.getPath(), getType(), instance);
    }

    @Override
    public Optional<String> getValueDescription(MutableModelNode modelNode) {
        Object instance = this.asImmutable(ModelType.untyped(), modelNode, null).getInstance();
        if (instance == null) {
            if (isPrimitiveType(getType())) {
                if (getType().equals(ModelType.of(char.class))) {
                    return Optional.of("");
                } else {
                    return Optional.of(String.valueOf(defaultValueOf(getType())));
                }
            }
            if (isScalarType(getType())) {
                return Optional.of("null");
            }
            return Optional.absent();
        }
        if (hasDefaultToString(instance)) {
            return Optional.absent();
        }
        return Optional.of(toStringValueDescription(instance));
    }
}
