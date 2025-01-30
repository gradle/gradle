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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.NonExtensible;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.artifacts.transform.TransformSpec;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.TransformRegistration;
import org.gradle.api.internal.artifacts.VariantTransformRegistry;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolated.IsolationScheme;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.service.ServiceLookup;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultVariantTransformRegistry implements VariantTransformRegistry {
    private final Set<TransformRegistration> registeredTransforms = new LinkedHashSet<>();

    private final AttributesFactory attributesFactory;
    private final ServiceLookup services;
    private final InstantiatorFactory instantiatorFactory;
    private final InstantiationScheme parametersInstantiationScheme;
    private final TransformRegistrationFactory registrationFactory;
    @SuppressWarnings({"unchecked", "rawtypes"})
    private final IsolationScheme<TransformAction<?>, TransformParameters> isolationScheme = new IsolationScheme<TransformAction<?>, TransformParameters>((Class)TransformAction.class, TransformParameters.class, TransformParameters.None.class);
    private final DocumentationRegistry documentationRegistry;

    public DefaultVariantTransformRegistry(
        InstantiatorFactory instantiatorFactory,
        AttributesFactory attributesFactory,
        ServiceLookup services,
        TransformRegistrationFactory registrationFactory,
        InstantiationScheme parametersInstantiationScheme,
        DocumentationRegistry documentationRegistry
    ) {
        this.instantiatorFactory = instantiatorFactory;
        this.attributesFactory = attributesFactory;
        this.services = services;
        this.registrationFactory = registrationFactory;
        this.parametersInstantiationScheme = parametersInstantiationScheme;
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public <T extends TransformParameters> void registerTransform(Class<? extends TransformAction<T>> actionType, Action<? super TransformSpec<T>> registrationAction) {
        doRegisterTransform(actionType, registrationAction);
    }

    @Override
    public Set<TransformRegistration> getRegistrations() {
        return ImmutableSet.copyOf(registeredTransforms);
    }

    private <T extends TransformParameters> void doRegisterTransform(Class<? extends TransformAction<T>> actionType, Action<? super TransformSpec<T>> registrationAction) {
        validateActionType((Class<? extends TransformAction<?>>) actionType);

        TypedRegistration<T> registration = null;
        try {
            Class<T> parameterType = isolationScheme.parameterTypeFor(actionType);
            T parameterObject = parameterType == null ? null : parametersInstantiationScheme.withServices(services).instantiator().newInstance(parameterType);
            registration = Cast.uncheckedNonnullCast(instantiatorFactory.decorateLenient(services).newInstance(TypedRegistration.class, parameterObject, attributesFactory));
            registrationAction.execute(registration);
            registration.validateAttributes();

            TransformRegistration finalizedRegistration = registrationFactory.create(registration.from.asImmutable(), registration.to.asImmutable(), actionType, parameterObject);
            registeredTransforms.add(finalizedRegistration);
        } catch (Exception e) {
            throw new VariantTransformConfigurationException(buildFailureToRegisterMsg(registration, actionType), e, documentationRegistry);
        }
    }

    private String buildFailureToRegisterMsg(@Nullable TypedRegistration<?> registration, Class<? extends TransformAction<?>> actionType) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Could not register artifact transform ");
        formatter.appendType(actionType);
        if (registration != null && !(registration.from.isEmpty() && registration.to.isEmpty())) {
            formatter.append(" (");
            if (!registration.from.isEmpty()) {
                formatter.append("from ");
                formatter.appendValue(registration.from);
            }
            if (!registration.to.isEmpty()) {
                if (!registration.from.isEmpty()) {
                    formatter.append(" ");
                }
                formatter.append("to ");
                formatter.appendValue(registration.to);
            }
            formatter.append(")");
        }
        formatter.append(".");
        return formatter.toString();
    }

    private <T> void validateActionType(@Nullable Class<T> actionType) {
        if (actionType == null) {
            throw new IllegalArgumentException("An artifact transform action type must be provided.");
        }
    }

    @NonExtensible
    public static abstract class TypedRegistration<T extends TransformParameters> implements TransformSpec<T> {
        private final AttributeContainerInternal from;
        private final AttributeContainerInternal to;
        private final T parameterObject;

        @Inject
        protected abstract DocumentationRegistry getDocumentationRegistry();

        public TypedRegistration(@Nullable T parameterObject, AttributesFactory attributesFactory) {
            this.parameterObject = parameterObject;
            this.from = attributesFactory.mutable();
            this.to = attributesFactory.mutable();
        }

        @Override
        public AttributeContainer getFrom() {
            return from;
        }

        @Override
        public AttributeContainer getTo() {
            return to;
        }

        @Override
        public T getParameters() {
            if (parameterObject == null) {
                throw new IllegalStateException("Cannot query parameters for artifact transform without parameters.");
            }
            return parameterObject;
        }

        @Override
        public void parameters(Action<? super T> action) {
            if (parameterObject == null) {
                throw new IllegalStateException("Cannot configure parameters for artifact transform without parameters.");
            }
            action.execute(parameterObject);
        }

        public void validateAttributes() {
            if (to.isEmpty()) {
                throw new VariantTransformConfigurationException("At least one 'to' attribute must be provided.", getDocumentationRegistry());
            }
            if (from.isEmpty()) {
                throw new VariantTransformConfigurationException("At least one 'from' attribute must be provided.", getDocumentationRegistry());
            }
            if (!from.keySet().containsAll(to.keySet())) {
                throw new VariantTransformConfigurationException("Each 'to' attribute must be included as a 'from' attribute.", getDocumentationRegistry());
            }
        }
    }
}
