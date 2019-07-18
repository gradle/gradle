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
import com.google.common.reflect.TypeToken;
import org.gradle.api.Action;
import org.gradle.api.ActionConfiguration;
import org.gradle.api.NonExtensible;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.artifacts.transform.TransformSpec;
import org.gradle.api.artifacts.transform.VariantTransform;
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
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.model.internal.type.ModelType;

import javax.annotation.Nullable;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;

public class DefaultVariantTransformRegistry implements VariantTransformRegistry {
    private static final Object[] NO_PARAMETERS = new Object[0];
    private final List<ArtifactTransformRegistration> transforms = Lists.newArrayList();
    private final ImmutableAttributesFactory immutableAttributesFactory;
    private final ServiceRegistry services;
    private final InstantiatorFactory instantiatorFactory;
    private final InstantiationScheme parametersInstantiationScheme;
    private final TransformationRegistrationFactory registrationFactory;

    public DefaultVariantTransformRegistry(InstantiatorFactory instantiatorFactory, ImmutableAttributesFactory immutableAttributesFactory, ServiceRegistry services, TransformationRegistrationFactory registrationFactory, InstantiationScheme parametersInstantiationScheme) {
        this.instantiatorFactory = instantiatorFactory;
        this.immutableAttributesFactory = immutableAttributesFactory;
        this.services = services;
        this.registrationFactory = registrationFactory;
        this.parametersInstantiationScheme = parametersInstantiationScheme;
    }

    @Override
    public void registerTransform(Action<? super VariantTransform> registrationAction) {
        UntypedRegistration registration = instantiatorFactory.decorateLenient().newInstance(UntypedRegistration.class, immutableAttributesFactory, instantiatorFactory);
        registrationAction.execute(registration);

        validateActionType(registration.actionType);
        validateAttributes(registration);

        Object[] parameters = registration.getTransformParameters();
        try {
            ArtifactTransformRegistration finalizedRegistration = registrationFactory.create(registration.from.asImmutable(), registration.to.asImmutable(), registration.actionType, parameters);
            transforms.add(finalizedRegistration);
        } catch (Exception e) {
            throw new VariantTransformConfigurationException(String.format("Cannot register artifact transform %s with parameters %s", ModelType.of(registration.actionType).getDisplayName(), Arrays.toString(parameters)), e);
        }
    }

    @Override
    public <T extends TransformParameters> void registerTransform(Class<? extends TransformAction<T>> actionType, Action<? super TransformSpec<T>> registrationAction) {
        ParameterizedType superType = (ParameterizedType) TypeToken.of(actionType).getSupertype(TransformAction.class).getType();
        Class<T> parameterType = Cast.uncheckedNonnullCast(TypeToken.of(superType.getActualTypeArguments()[0]).getRawType());
        if (parameterType == TransformParameters.class) {
            throw new VariantTransformConfigurationException(String.format("Could not register transform: must use a sub-type of %s as parameter type. Use %s for transforms without parameters.", ModelType.of(TransformParameters.class).getDisplayName(), ModelType.of(TransformParameters.None.class).getDisplayName()));
        }
        T parameterObject = parameterType == TransformParameters.None.class ? null : parametersInstantiationScheme.withServices(services).instantiator().newInstance(parameterType);
        TypedRegistration<T> registration = Cast.uncheckedNonnullCast(instantiatorFactory.decorateLenient().newInstance(TypedRegistration.class, parameterObject, immutableAttributesFactory));
        registrationAction.execute(registration);

        register(registration, actionType, parameterObject);
    }

    private <T extends TransformParameters> void register(RecordingRegistration registration, Class<? extends TransformAction> actionType, @Nullable T parameterObject) {
        validateActionType(actionType);
        validateAttributes(registration);
        try {
            ArtifactTransformRegistration finalizedRegistration = registrationFactory.create(registration.from.asImmutable(), registration.to.asImmutable(), actionType, parameterObject);
            transforms.add(finalizedRegistration);
        } catch (Exception e) {
            throw new VariantTransformConfigurationException(String.format("Cannot register artifact transform %s (from %s to %s)", ModelType.of(actionType).getDisplayName(), registration.from, registration.to), e);
        }
    }

    private static <T> void validateActionType(@Nullable Class<T> actionType) {
        if (actionType == null) {
            throw new VariantTransformConfigurationException("Could not register transform: an artifact transform action must be provided.");
        }
    }

    private static void validateAttributes(RecordingRegistration registration) {
        if (registration.to.isEmpty()) {
            throw new VariantTransformConfigurationException("Could not register transform: at least one 'to' attribute must be provided.");
        }
        if (registration.from.isEmpty()) {
            throw new VariantTransformConfigurationException("Could not register transform: at least one 'from' attribute must be provided.");
        }
        if (!registration.from.keySet().containsAll(registration.to.keySet())) {
            throw new VariantTransformConfigurationException("Could not register transform: each 'to' attribute must be included as a 'from' attribute.");
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
    public static class UntypedRegistration extends RecordingRegistration implements VariantTransform {
        private Action<? super ActionConfiguration> configAction;
        private final InstantiatorFactory instantiatorFactory;
        Class<? extends ArtifactTransform> actionType;

        public UntypedRegistration(ImmutableAttributesFactory immutableAttributesFactory, InstantiatorFactory instantiatorFactory) {
            super(immutableAttributesFactory);
            this.instantiatorFactory = instantiatorFactory;
        }

        @Override
        public void artifactTransform(Class<? extends ArtifactTransform> type) {
            artifactTransform(type, null);
        }

        @Override
        public void artifactTransform(Class<? extends ArtifactTransform> type, @Nullable Action<? super ActionConfiguration> config) {
            if (this.actionType != null) {
                throw new VariantTransformConfigurationException("Could not register transform: only one ArtifactTransform may be provided for registration.");
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
                throw new VariantTransformConfigurationException("Cannot query parameters for artifact transform without parameters.");
            }
            return parameterObject;
        }

        @Override
        public void parameters(Action<? super T> action) {
            if (parameterObject == null) {
                throw new VariantTransformConfigurationException("Cannot configure parameters for artifact transform without parameters.");
            }
            action.execute(parameterObject);
        }
    }
}
