/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.platform.base.component.internal;

import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.ModelInstantiationException;
import org.gradle.platform.base.internal.ComponentSpecIdentifier;

public class DefaultComponentSpec extends AbstractComponentSpec {
    private static final ThreadLocal<ComponentInfo> NEXT_COMPONENT_INFO = new ThreadLocal<ComponentInfo>();

    public static <T extends DefaultComponentSpec> T create(Class<? extends ComponentSpec> publicType, Class<T> implementationType, ComponentSpecIdentifier identifier, MutableModelNode modelNode) {
        NEXT_COMPONENT_INFO.set(new ComponentInfo(identifier, modelNode, publicType));
        try {
            try {
                return DirectInstantiator.INSTANCE.newInstance(implementationType);
            } catch (ObjectInstantiationException e) {
                throw new ModelInstantiationException(String.format("Could not create component of type %s", publicType.getSimpleName()), e.getCause());
            }
        } finally {
            NEXT_COMPONENT_INFO.set(null);
        }
    }

    protected static ComponentInfo getInfo() {
        return NEXT_COMPONENT_INFO.get();
    }

    public DefaultComponentSpec() {
        this(getInfo());
    }

    public DefaultComponentSpec(ComponentInfo info) {
        super(validate(info).componentIdentifier, info.publicType);
    }

    private static ComponentInfo validate(ComponentInfo info) {
        if (info == null) {
            throw new ModelInstantiationException("Direct instantiation of a BaseComponentSpec is not permitted. Use a @ComponentType rule instead.");
        }
        return info;
    }

    protected static class ComponentInfo {
        public final ComponentSpecIdentifier componentIdentifier;
        public final MutableModelNode modelNode;
        public final Class<?> publicType;

        private ComponentInfo(
            ComponentSpecIdentifier componentIdentifier,
            MutableModelNode modelNode,
            Class<?> publicType
        ) {
            this.componentIdentifier = componentIdentifier;
            this.modelNode = modelNode;
            this.publicType = publicType;
        }
    }
}
