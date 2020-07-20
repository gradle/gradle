/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.provider;

import org.gradle.api.ProjectConfigurationException;
import org.gradle.api.Transformer;
import org.gradle.api.credentials.AwsCredentials;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.internal.DisplayName;
import org.gradle.internal.credentials.DefaultAwsCredentials;
import org.gradle.internal.credentials.DefaultPasswordCredentials;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CredentialsProviderFactory implements TaskExecutionGraphListener {

    private final ProviderFactory providerFactory;
    private final GradleProperties gradleProperties;

    private final Map<String, Provider<PasswordCredentials>> passwordProviders = new ConcurrentHashMap<>();
    private final Map<String, Provider<AwsCredentials>> awsProviders = new ConcurrentHashMap<>();

    private final Map<String, InterceptingProvider<?>> requiredByExecution = new ConcurrentHashMap<>();

    private final Set<String> missingProviderErrors = ConcurrentHashMap.newKeySet();

    public CredentialsProviderFactory(ProviderFactory providerFactory, GradleProperties gradleProperties) {
        this.providerFactory = providerFactory;
        this.gradleProperties = gradleProperties;
    }

    @SuppressWarnings("unchecked")
    public <T extends Credentials> Provider<T> provide(Class<T> credentialsType, String identity) {
        validateIdentity(identity);

        if (PasswordCredentials.class.isAssignableFrom(credentialsType)) {
            return (Provider<T>) passwordProviders.computeIfAbsent(identity, this::passwordCredentialsProvider);
        }
        if (AwsCredentials.class.isAssignableFrom(credentialsType)) {
            return (Provider<T>) awsProviders.computeIfAbsent(identity, this::awsCredentialsProvider);
        }

        throw new IllegalArgumentException(String.format("Unsupported credentials type: %s", credentialsType));
    }

    public <T extends Credentials> Provider<T> provide(Class<T> credentialsType, Provider<String> identity) {
        return new DefaultProvider<>(() -> provide(credentialsType, identity.get()).getOrNull());
    }

    @Override
    public void graphPopulated(TaskExecutionGraph graph) {
        requiredByExecution.values().forEach(p -> {
            p.assertRequiredPropertiesPresent(gradleProperties);
        });
        if (!missingProviderErrors.isEmpty()) {
            throw new ProjectConfigurationException("Credentials required for this build could not be resolved.",
                missingProviderErrors.stream().map(MissingValueException::new).collect(Collectors.toList()));
        }
    }

    private static void validateIdentity(@Nullable String identity) {
        if (identity == null || identity.isEmpty() || !identity.chars().allMatch(Character::isLetterOrDigit)) {
            throw new IllegalArgumentException("Identity may contain only letters and digits, received: " + identity);
        }
    }

    private Provider<PasswordCredentials> passwordCredentialsProvider(String identity) {
        String usernameProperty = identity + "Username";
        String passwordProperty = identity + "Password";
        Provider<PasswordCredentials> provider = providerFactory.of(
            PasswordCredentialsValueSource.class,
            spec -> {
                spec.getParameters().getIdentity().set(identity);
                spec.getParameters().getUsernameProperty().set(usernameProperty);
                spec.getParameters().getPasswordProperty().set(passwordProperty);
            }
        );
        return new InterceptingProvider<>(provider, identity, Arrays.asList(usernameProperty, passwordProperty));
    }

    private Provider<AwsCredentials> awsCredentialsProvider(String identity) {
        String accessKeyProperty = identity + "AccessKey";
        String secretKeyProperty = identity + "SecretKey";
        String sessionTokenProperty = identity + "SessionToken";
        Provider<AwsCredentials> provider = providerFactory.of(
            AwsCredentialsValueSource.class,
            spec -> {
                spec.getParameters().getIdentity().set(identity);
                spec.getParameters().getAccessKeyProperty().set(accessKeyProperty);
                spec.getParameters().getSecretKeyProperty().set(secretKeyProperty);
                spec.getParameters().getSessionTokenProperty().set(sessionTokenProperty);
            }
        );
        return new InterceptingProvider<>(provider, identity, Arrays.asList(accessKeyProperty, secretKeyProperty));
    }

    @Nullable
    static String getMissingPropertiesError(String identity, List<String> requiredProperties, GradleProperties gradleProperties) {
        List<String> missingProperties = new ArrayList<>();
        for (String property : requiredProperties) {
            if (gradleProperties.find(property) == null) {
                missingProperties.add(property);
            }
        }
        if (missingProperties.isEmpty()) {
            return null;
        }
        TreeFormatter errorBuilder = new TreeFormatter();
        errorBuilder.node("The following Gradle properties are missing for '").append(identity).append("' credentials");
        errorBuilder.startChildren();
        for (String missingProperty : missingProperties) {
            errorBuilder.node(missingProperty);
        }
        errorBuilder.endChildren();
        return errorBuilder.toString();
    }

    static void assertRequiredValuesPresent(String identity, List<String> requiredProperties, GradleProperties gradleProperties) {
        String missingPropertiesError = getMissingPropertiesError(identity, requiredProperties, gradleProperties);
        if (missingPropertiesError != null) {
            throw new MissingValueException(missingPropertiesError);
        }
    }

    private class InterceptingProvider<T> implements ProviderInternal<T> {

        private final ProviderInternal<T> delegate;
        private final String identity;
        private final List<String> requiredProperties;

        public InterceptingProvider(Provider<T> delegate, String identity, List<String> requiredProperties) {
            this.delegate = (ProviderInternal<T>) delegate.forUseAtConfigurationTime();
            this.identity = identity;
            this.requiredProperties = requiredProperties;
        }

        void assertRequiredPropertiesPresent(GradleProperties gradleProperties) {
            String error = CredentialsProviderFactory.getMissingPropertiesError(identity, requiredProperties, gradleProperties);
            if (error != null) {
                missingProviderErrors.add(error);
            }
        }

        @Override
        public ValueProducer getProducer() {
            requiredByExecution.putIfAbsent(identity, this);
            return delegate.getProducer();
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return delegate.calculatePresence(consumer);
        }

        @Nullable
        @Override
        public Class<T> getType() {
            return delegate.getType();
        }

        @Override
        public T get() {
            return delegate.get();
        }

        @Nullable
        @Override
        public T getOrNull() {
            return delegate.getOrNull();
        }

        @Override
        public T getOrElse(T defaultValue) {
            return delegate.getOrElse(defaultValue);
        }

        @Override
        public <S> ProviderInternal<S> map(Transformer<? extends S, ? super T> transformer) {
            return delegate.map(transformer);
        }

        @Override
        public <S> Provider<S> flatMap(Transformer<? extends Provider<? extends S>, ? super T> transformer) {
            return delegate.flatMap(transformer);
        }

        @Override
        public boolean isPresent() {
            return delegate.isPresent();
        }

        @Override
        public Provider<T> orElse(T value) {
            return delegate.orElse(value);
        }

        @Override
        public Provider<T> orElse(Provider<? extends T> provider) {
            return delegate.orElse(provider);
        }

        @Override
        public Provider<T> forUseAtConfigurationTime() {
            return delegate.forUseAtConfigurationTime();
        }

        @Override
        public Value<? extends T> calculateValue(ValueConsumer consumer) {
            return delegate.calculateValue(consumer);
        }

        @Override
        public ProviderInternal<T> asSupplier(DisplayName owner, Class<? super T> targetType, ValueSanitizer<? super T> sanitizer) {
            return this;
        }

        @Override
        public ProviderInternal<T> withFinalValue(ValueConsumer consumer) {
            return delegate.withFinalValue(consumer);
        }

        @Override
        public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
            //return ExecutionTimeValue.changingValue(delegate);
            return delegate.calculateExecutionTimeValue();
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            requiredByExecution.putIfAbsent(identity, this);
            delegate.visitDependencies(context);
        }
    }

}

abstract class PasswordCredentialsValueSource implements ValueSource<PasswordCredentials, PasswordCredentialsValueSource.Parameters> {

    private final GradleProperties gradleProperties;

    public interface Parameters extends ValueSourceParameters {
        Property<String> getIdentity();

        Property<String> getUsernameProperty();

        Property<String> getPasswordProperty();
    }

    @Inject
    public PasswordCredentialsValueSource(GradleProperties gradleProperties) {
        this.gradleProperties = gradleProperties;
    }

    @Nullable
    @Override
    public PasswordCredentials obtain() {
        String usernamePropertyName = getParameters().getUsernameProperty().get();
        String passwordPropertyName = getParameters().getPasswordProperty().get();
        CredentialsProviderFactory.assertRequiredValuesPresent(getParameters().getIdentity().get(), Arrays.asList(usernamePropertyName, passwordPropertyName), gradleProperties);

        String usernameValue = gradleProperties.find(usernamePropertyName);
        String passwordValue = gradleProperties.find(passwordPropertyName);
        return new DefaultPasswordCredentials(usernameValue, passwordValue);
    }

}

abstract class AwsCredentialsValueSource implements ValueSource<AwsCredentials, AwsCredentialsValueSource.Parameters> {

    private final GradleProperties gradleProperties;

    public interface Parameters extends ValueSourceParameters {
        Property<String> getIdentity();

        Property<String> getAccessKeyProperty();

        Property<String> getSecretKeyProperty();

        Property<String> getSessionTokenProperty();
    }

    @Inject
    public AwsCredentialsValueSource(GradleProperties gradleProperties) {
        this.gradleProperties = gradleProperties;
    }

    @Nullable
    @Override
    public AwsCredentials obtain() {
        String accessKeyPropertyName = getParameters().getAccessKeyProperty().get();
        String secretKeyPropertyName = getParameters().getSecretKeyProperty().get();
        CredentialsProviderFactory.assertRequiredValuesPresent(getParameters().getIdentity().get(), Arrays.asList(accessKeyPropertyName, secretKeyPropertyName), gradleProperties);

        String accessKey = gradleProperties.find(accessKeyPropertyName);
        String secretKey = gradleProperties.find(secretKeyPropertyName);
        String sessionToken = gradleProperties.find(getParameters().getSessionTokenProperty().get());

        AwsCredentials credentials = new DefaultAwsCredentials();
        credentials.setAccessKey(accessKey);
        credentials.setSecretKey(secretKey);
        credentials.setSessionToken(sessionToken);
        return credentials;
    }

}

