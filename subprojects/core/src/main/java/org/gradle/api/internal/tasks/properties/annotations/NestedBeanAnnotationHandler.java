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

package org.gradle.api.internal.tasks.properties.annotations;

import com.google.common.annotations.VisibleForTesting;
import org.codehaus.groovy.runtime.ConvertedClosure;
import org.gradle.api.internal.tasks.DefaultTaskInputPropertySpec;
import org.gradle.api.internal.tasks.PropertySpecFactory;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.api.internal.tasks.ValidatingValue;
import org.gradle.api.internal.tasks.ValidationAction;
import org.gradle.api.internal.tasks.properties.NestedBeanContext;
import org.gradle.api.internal.tasks.properties.NestedBeanResolver;
import org.gradle.api.internal.tasks.properties.PropertyNode;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.tasks.Nested;
import org.gradle.internal.UncheckedException;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import static org.gradle.api.internal.tasks.TaskValidationContext.Severity.ERROR;

public class NestedBeanAnnotationHandler implements PropertyAnnotationHandler {

    private NestedBeanResolver<PropertyNode> nestedBeanResolver = new NestedBeanResolver<PropertyNode>();

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Nested.class;
    }

    @Override
    public void visitPropertyValue(PropertyValue propertyValue, PropertyVisitor visitor, PropertySpecFactory specFactory, NestedBeanContext<PropertyNode> context) {
        Object nested;
        try {
            nested = propertyValue.getValue();
        } catch (Exception e) {
            visitor.visitInputProperty(specFactory.createInputPropertySpec(propertyValue.getPropertyName(), new InvalidPropertyValue(e)));
            return;
        }
        if (nested != null) {
            nestedBeanResolver.resolve(
                context.createNode(propertyValue.getPropertyName(), nested),
                new ImplementationAddingPropertyContext(context, visitor, specFactory));
        } else if (!propertyValue.isOptional()) {
            visitor.visitInputProperty(specFactory.createInputPropertySpec(propertyValue.getPropertyName(), new AbsentPropertyValue()));
        }
    }

    private static class ImplementationAddingPropertyContext implements NestedBeanContext<PropertyNode> {
        private final NestedBeanContext<PropertyNode> delegate;
        private final PropertyVisitor visitor;
        private final PropertySpecFactory specFactory;

        public ImplementationAddingPropertyContext(NestedBeanContext<PropertyNode> delegate, PropertyVisitor visitor, PropertySpecFactory specFactory) {
            this.delegate = delegate;
            this.visitor = visitor;
            this.specFactory = specFactory;
        }

        @Override
        public PropertyNode createNode(String propertyName, Object nested) {
            return delegate.createNode(propertyName, nested);
        }

        @Override
        public void addNested(PropertyNode node) {
            visitImplementation(node, visitor, specFactory);
            delegate.addNested(node);
        }
    }

    private static void visitImplementation(PropertyNode node, PropertyVisitor visitor, PropertySpecFactory specFactory) {
        // The root bean (Task) implementation is currently tracked separately
        DefaultTaskInputPropertySpec implementation = specFactory.createInputPropertySpec(node.getQualifiedPropertyName("class"), new ImplementationPropertyValue(getImplementationClass(node.getBean())));
        implementation.optional(false);
        visitor.visitInputProperty(implementation);
    }

    @VisibleForTesting
    static Class<?> getImplementationClass(Object bean) {
        // When Groovy coerces a Closure into an SAM type, then it creates a Proxy which is backed by the Closure.
        // We want to track the implementation of the Closure, since the class name and classloader of the proxy will not change.
        // Java and Kotlin Lambdas are coerced to SAM types at compile time, so no unpacking is necessary there.
        if (Proxy.isProxyClass(bean.getClass())) {
            InvocationHandler invocationHandler = Proxy.getInvocationHandler(bean);
            if (invocationHandler instanceof ConvertedClosure) {
                Object delegate = ((ConvertedClosure) invocationHandler).getDelegate();
                return delegate.getClass();
            }
            return invocationHandler.getClass();
        }
        return bean.getClass();
    }

    private static class ImplementationPropertyValue implements ValidatingValue {

        private final Class<?> beanClass;

        public ImplementationPropertyValue(Class<?> beanClass) {
            this.beanClass = beanClass;
        }

        @Override
        public Object call() {
            return beanClass;
        }

        @Override
        public void validate(String propertyName, boolean optional, ValidationAction valueValidator, TaskValidationContext context) {
        }
    }

    private static class InvalidPropertyValue implements ValidatingValue {
        private final Exception exception;

        public InvalidPropertyValue(Exception exception) {
            this.exception = exception;
        }

        @Nullable
        @Override
        public Object call() {
            return null;
        }

        @Override
        public void validate(String propertyName, boolean optional, ValidationAction valueValidator, TaskValidationContext context) {
            throw UncheckedException.throwAsUncheckedException(exception);
        }
    }

    private static class AbsentPropertyValue implements ValidatingValue {
        @Nullable
        @Override
        public Object call() {
            return null;
        }

        @Override
        public void validate(String propertyName, boolean optional, ValidationAction valueValidator, TaskValidationContext context) {
            context.recordValidationMessage(ERROR, String.format("No value has been specified for property '%s'.", propertyName));
        }

    }
}
