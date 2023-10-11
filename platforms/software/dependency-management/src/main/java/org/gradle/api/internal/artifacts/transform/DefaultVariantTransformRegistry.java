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

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.NonExtensible;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.artifacts.transform.TransformSpec;
import org.gradle.api.artifacts.transform.VariantTransformConfigurationException;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.TransformRegistration;
import org.gradle.api.internal.artifacts.VariantTransformRegistry;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolated.IsolationScheme;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.service.ServiceRegistry;

import javax.annotation.Nullable;
import java.util.List;

public class DefaultVariantTransformRegistry implements VariantTransformRegistry {
    private final List<TransformRegistration> registrations = Lists.newArrayList();
    private final ImmutableAttributesFactory immutableAttributesFactory;
    private final ServiceRegistry services;
    private final InstantiatorFactory instantiatorFactory;
    private final InstantiationScheme parametersInstantiationScheme;
    private final TransformRegistrationFactory registrationFactory;
    @SuppressWarnings("unchecked")
    private final IsolationScheme<TransformAction<?>, TransformParameters> isolationScheme = new IsolationScheme<TransformAction<?>, TransformParameters>((Class)TransformAction.class, TransformParameters.class, TransformParameters.None.class);

    public DefaultVariantTransformRegistry(InstantiatorFactory instantiatorFactory, ImmutableAttributesFactory immutableAttributesFactory, ServiceRegistry services, TransformRegistrationFactory registrationFactory, InstantiationScheme parametersInstantiationScheme) {
        this.instantiatorFactory = instantiatorFactory;
        this.immutableAttributesFactory = immutableAttributesFactory;
        this.services = services;
        this.registrationFactory = registrationFactory;
        this.parametersInstantiationScheme = parametersInstantiationScheme;
    }

    @Override
    public <T extends TransformParameters> void registerTransform(Class<? extends TransformAction<T>> actionType, Action<? super TransformSpec<T>> registrationAction) {
        TypedRegistration<T> registration = null;
        try {
            Class<T> parameterType = isolationScheme.parameterTypeFor(actionType);
            T parameterObject = parameterType == null ? null : parametersInstantiationScheme.withServices(services).instantiator().newInstance(parameterType);
            registration = Cast.uncheckedNonnullCast(instantiatorFactory.decorateLenient().newInstance(TypedRegistration.class, parameterObject, immutableAttributesFactory));
            registrationAction.execute(registration);
            register(registration, actionType, parameterObject);
        } catch (VariantTransformConfigurationException e) {
            throw e;
        } catch (Exception e) {
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
            throw new VariantTransformConfigurationException(formatter.toString(), e);
        }
    }

    private <T extends TransformParameters> void register(RecordingRegistration registration, Class<? extends TransformAction<?>> actionType, @Nullable T parameterObject) {
        validateActionType(actionType);
        try {
            validateAttributes(registration);
            TransformRegistration finalizedRegistration = registrationFactory.create(registration.from.asImmutable(), registration.to.asImmutable(), actionType, parameterObject);
            registrations.add(finalizedRegistration);
        } catch (Exception e) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Could not register artifact transform ");
            formatter.appendType(actionType);
            formatter.append(" (from ");
            formatter.appendValue(registration.from);
            formatter.append(" to ");
            formatter.appendValue(registration.to);
            formatter.append(").");
            throw new VariantTransformConfigurationException(formatter.toString(), e);
        }
    }

    private static <T> void validateActionType(@Nullable Class<T> actionType) {
        if (actionType == null) {
            throw new IllegalArgumentException("An artifact transform action type must be provided.");
        }
    }

    private static void validateAttributes(RecordingRegistration registration) {
        if (registration.to.isEmpty()) {
            throw new IllegalArgumentException("At least one 'to' attribute must be provided.");
        }
        if (registration.from.isEmpty()) {
            throw new IllegalArgumentException("At least one 'from' attribute must be provided.");
        }
        if (!registration.from.keySet().containsAll(registration.to.keySet())) {
            throw new IllegalArgumentException("Each 'to' attribute must be included as a 'from' attribute.");
        }
    }

    @Override
    public List<TransformRegistration> getRegistrations() {
        return registrations;
    }

    public static abstract class RecordingRegistration {
        final AttributeContainerInternal from;
        final AttributeContainerInternal to;

        public RecordingRegistration(ImmutableAttributesFactory immutableAttributesFactory) {
            from = immutableAttributesFactory.mutable();
            to = immutableAttributesFactory.mutable();
        }

        public AttributeContainer getFrom() {
            return from;
        }

        public AttributeContainer getTo() {
            return to;
        }
    }

    @NonExtensible
    public static class TypedRegistration<T extends TransformParameters> extends RecordingRegistration implements TransformSpec<T> {
        private final T parameterObject;

        public TypedRegistration(@Nullable T parameterObject, ImmutableAttributesFactory immutableAttributesFactory) {
            super(immutableAttributesFactory);
            this.parameterObject = parameterObject;
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
    }
}
