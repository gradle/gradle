/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.properties.bean;

import com.google.common.annotations.VisibleForTesting;
import org.codehaus.groovy.runtime.ConvertedClosure;
import org.gradle.api.internal.tasks.properties.TypeMetadata;
import org.gradle.api.internal.tasks.properties.TypeMetadataStore;
import org.gradle.internal.snapshot.impl.ImplementationValue;
import org.gradle.util.internal.ClosureBackedAction;
import org.gradle.util.internal.ConfigureUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;

public class RuntimeBeanNodeFactory {

    private final TypeMetadataStore metadataStore;
    private ImplementationIdentifier implementationIdentifier;

    public RuntimeBeanNodeFactory(TypeMetadataStore metadataStore, ImplementationIdentifier implementationIdentifier) {
        this.metadataStore = metadataStore;
        this.implementationIdentifier = implementationIdentifier;
    }

    public RuntimeBeanNode<?> createRoot(Object bean) {
        return new RootRuntimeBeanNode(bean, metadataStore.getTypeMetadata(bean.getClass()));
    }

    public RuntimeBeanNode<?> create(RuntimeBeanNode parentNode, String propertyName, Object bean) {
        parentNode.checkCycles(propertyName, bean);
        TypeMetadata typeMetadata = metadataStore.getTypeMetadata(bean.getClass());
        if (!typeMetadata.hasAnnotatedProperties()) {
            if (bean instanceof Map<?, ?>) {
                return new MapRuntimeBeanNode(parentNode, propertyName, (Map<?, ?>) bean, typeMetadata);
            }
            if (bean instanceof Iterable<?>) {
                return new IterableRuntimeBeanNode(parentNode, propertyName, (Iterable<?>) bean, typeMetadata);
            }
        }
        Object unwrapped = unwrapBean(bean);
//        String classIdentifier = ScriptOriginUtil.getOriginClassIdentifier(unwrapped);
//        ImplementationValue implementation = new ImplementationValue(classIdentifier, unwrapped);
        ImplementationValue implementation = implementationIdentifier.identify(unwrapped);
        return new NestedRuntimeBeanNode(parentNode, propertyName, bean, implementation, typeMetadata);
    }

    @VisibleForTesting
    static Object unwrapBean(Object bean) {
        // When Groovy coerces a Closure into an SAM type, then it creates a Proxy which is backed by the Closure.
        // We want to track the implementation of the Closure, since the class name and classloader of the proxy will not change.
        // Java and Kotlin Lambdas are coerced to SAM types at compile time, so no unpacking is necessary there.
        if (Proxy.isProxyClass(bean.getClass())) {
            InvocationHandler invocationHandler = Proxy.getInvocationHandler(bean);
            if (invocationHandler instanceof ConvertedClosure) {
                return ((ConvertedClosure) invocationHandler).getDelegate();
            }
            return invocationHandler;
        }

        // Same as above, if we have wrapped a closure in a WrappedConfigureAction or a ClosureBackedAction, we want to
        // track the closure itself, not the action class.
        if (bean instanceof ConfigureUtil.WrappedConfigureAction) {
            return ((ConfigureUtil.WrappedConfigureAction<?>) bean).getConfigureClosure();
        }

        if (bean instanceof ClosureBackedAction) {
            return ((ClosureBackedAction<?>) bean).getClosure();
        }
        return bean;
    }
}
