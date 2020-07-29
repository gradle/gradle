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
import org.gradle.api.ActionConfiguration;
import org.gradle.api.NonExtensible;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.artifacts.transform.TransformSpec;
import org.gradle.api.artifacts.transform.VariantTransformConfigurationException;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.DefaultActionConfiguration;
import org.gradle.api.internal.artifacts.ArtifactTransformRegistration;
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
    private static final Object[] NO_PARAMETERS = new Object[0];
    private final List<ArtifactTransformRegistration> transforms = Lists.newArrayList();
    private final ImmutableAttributesFactory immutableAttributesFactory;
    private final ServiceRegistry services;
    private final InstantiatorFactory instantiatorFactory;
    private final InstantiationScheme parametersInstantiationScheme;
    private final TransformationRegistrationFactory registrationFactory;
    @SuppressWarnings("unchecked")
    private final IsolationScheme<TransformAction<?>, TransformParameters> isolationScheme = new IsolationScheme<TransformAction<?>, TransformParameters>((Class)TransformAction.class, TransformParameters.class, TransformParameters.None.class);

    public DefaultVariantTransformRegistry(InstantiatorFactory instantiatorFactory, ImmutableAttributesFactory immutableAttributesFactory, ServiceRegistry services, TransformationRegistrationFactory registrationFactory, InstantiationScheme parametersInstantiationScheme) {
        this.instantiatorFactory = instantiatorFactory;
        this.immutableAttributesFactory = immutableAttributesFactory;
        this.services = services;
        this.registrationFactory = registrationFactory;
        this.parametersInstantiationScheme = parametersInstantiationScheme;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void registerTransform(Action<? super org.gradle.api.artifacts.transform.VariantTransform> registrationAction) {
        try {
            UntypedRegistration registration = instantiatorFactory.decorateLenient().newInstance(UntypedRegistration.class, immutableAttributesFactory, instantiatorFactory);
            registrationAction.execute(registration);

            validateActionType(registration.actionType);
            try {
                validateAttributes(registration);

                Object[] parameters = registration.getTransformParameters();
                ArtifactTransformRegistration finalizedRegistration = registrationFactory.create(registration.from.asImmutable(), registration.to.asImmutable(), registration.actionType, parameters);
                transforms.add(finalizedRegistration);
            } catch (Exception e) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("Could not register artifact transform ");
                formatter.appendType(registration.actionType);
                formatter.append(" (from ");
                formatter.appendValue(registration.from);
                formatter.append(" to ");
                formatter.appendValue(registration.to);
                formatter.append(").");
                throw new VariantTransformConfigurationException(formatter.toString(), e);
            }
        } catch (VariantTransformConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new VariantTransformConfigurationException("Could not register artifact transform.", e);
        }
    }

    @Override
    public <T extends TransformParameters> void registerTransform(Class<? extends TransformAction<T>> actionType, Action<? super TransformSpec<T>> registrationAction) {
        try {
            Class<T> parameterType = isolationScheme.parameterTypeFor(actionType);
            T parameterObject = parameterType == null ? null : parametersInstantiationScheme.withServices(services).instantiator().newInstance(parameterType);
            TypedRegistration<T> registration = Cast.uncheckedNonnullCast(instantiatorFactory.decorateLenient().newInstance(TypedRegistration.class, parameterObject, immutableAttributesFactory));
            registrationAction.execute(registration);
            register(registration, actionType, parameterObject);
        } catch (VariantTransformConfigurationException e) {
            throw e;
        } catch (Exception e) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Could not register artifact transform ");
            formatter.appendType(actionType);
            formatter.append(".");
            throw new VariantTransformConfigurationException(formatter.toString(), e);
        }
    }

    private <T extends TransformParameters> void register(RecordingRegistration registration, Class<? extends TransformAction<?>> actionType, @Nullable T parameterObject) {
        validateActionType(actionType);
        try {
            validateAttributes(registration);
            ArtifactTransformRegistration finalizedRegistration = registrationFactory.create(registration.from.asImmutable(), registration.to.asImmutable(), actionType, parameterObject);
            transforms.add(finalizedRegistration);
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
    public List<ArtifactTransformRegistration> getTransforms() {
        return transforms;
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

    @SuppressWarnings("deprecation")
    @NonExtensible
    public static class UntypedRegistration extends RecordingRegistration implements org.gradle.api.artifacts.transform.VariantTransform {
        private Action<? super ActionConfiguration> configAction;
        private final InstantiatorFactory instantiatorFactory;
        Class<? extends org.gradle.api.artifacts.transform.ArtifactTransform> actionType;

        public UntypedRegistration(ImmutableAttributesFactory immutableAttributesFactory, InstantiatorFactory instantiatorFactory) {
            super(immutableAttributesFactory);
            this.instantiatorFactory = instantiatorFactory;
        }

        @Override
        public void artifactTransform(Class<? extends org.gradle.api.artifacts.transform.ArtifactTransform> type) {
            artifactTransform(type, null);
        }

        @Override
        public void artifactTransform(Class<? extends org.gradle.api.artifacts.transform.ArtifactTransform> type, @Nullable Action<? super ActionConfiguration> config) {
            if (this.actionType != null) {
                throw new IllegalStateException("Only one ArtifactTransform may be provided for registration.");
            }
            this.actionType = type;
            this.configAction = config;
        }

        Object[] getTransformParameters() {
            if (configAction == null) {
                return NO_PARAMETERS;
            }
            ActionConfiguration config = instantiatorFactory.decorateLenient().newInstance(DefaultActionConfiguration.class);
            configAction.execute(config);
            return config.getParams();
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
