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

import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.credentials.AwsCredentials;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.api.internal.provider.sources.GradlePropertyValueSource;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.internal.tasks.WorkNodeAction;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.internal.Cast;
import org.gradle.internal.DisplayName;
import org.gradle.internal.credentials.DefaultAwsCredentials;
import org.gradle.internal.credentials.DefaultPasswordCredentials;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CredentialsProviderFactory {

    private final ProviderFactory providerFactory;

    public CredentialsProviderFactory(ProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    public <T extends Credentials> Provider<T> provide(Class<T> credentialsType, String identity) {
        validateIdentity(identity);

        return provide(credentialsType, providerFactory.provider(() -> identity));
    }

    public <T extends Credentials> Provider<T> provide(Class<T> credentialsType, Provider<String> identity) {
        if (PasswordCredentials.class.isAssignableFrom(credentialsType)) {
            return Cast.uncheckedCast(passwordCredentialsProvider(identity));
        }
        if (AwsCredentials.class.isAssignableFrom(credentialsType)) {
            return Cast.uncheckedCast(awsCredentialsProvider(identity));
        }

        throw new IllegalArgumentException(String.format("Unsupported credentials type: %s", credentialsType));
    }

    private static void validateIdentity(@Nullable String identity) {
        if (identity == null || identity.isEmpty() || !identity.chars().allMatch(Character::isLetterOrDigit)) {
            throw new IllegalArgumentException("Identity may contain only letters and digits, received: " + identity);
        }
    }

    private Provider<PasswordCredentials> passwordCredentialsProvider(Provider<String> identity) {
        Provider<PasswordCredentials> provider = providerFactory.of(
            PasswordCredentialsValueSource.class,
            spec -> spec.getParameters().getPropertyName().set(identity)
        );
        return new InterceptingProvider<>(provider);
    }

    private Provider<AwsCredentials> awsCredentialsProvider(Provider<String> identity) {
        Provider<AwsCredentials> provider = providerFactory.of(
            AwsCredentialsValueSource.class,
            spec -> spec.getParameters().getIdentity().set(identity)
        );
        return new InterceptingProvider<>(provider);
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

    private static class InterceptingProvider<T extends Credentials> implements ProviderInternal<T> {

        private final ProviderInternal<T> delegate;

        public InterceptingProvider(Provider<T> delegate) {
            this.delegate = (ProviderInternal<T>) delegate;
        }

        @Override
        public ValueProducer getProducer() {
            return delegate.getProducer().plus(ValueProducer.nodeAction(new ResolveCredentialsWorkNodeAction(this)));
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
            return delegate.calculateExecutionTimeValue();
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            context.add(new ResolveCredentialsWorkNodeAction(delegate));
            delegate.visitDependencies(context);
        }
    }

    public static class ResolveCredentialsWorkNodeAction implements WorkNodeAction {
        private final Provider<?> provider;

        public ResolveCredentialsWorkNodeAction(Provider<?> provider) {
            this.provider = provider;
        }

        public Provider<?> getProvider() {
            return provider;
        }

        @Nullable
        @Override
        public Project getProject() {
            return null;
        }

        @Override
        public void run(NodeExecutionContext context) {
            provider.get();
        }
    }

}

abstract class PasswordCredentialsValueSource implements ValueSource<PasswordCredentials, GradlePropertyValueSource.Parameters> {

    private final GradleProperties gradleProperties;

    @Inject
    public PasswordCredentialsValueSource(GradleProperties gradleProperties) {
        this.gradleProperties = gradleProperties;
    }

    @Nullable
    @Override
    public PasswordCredentials obtain() {
        String identity = getParameters().getPropertyName().get();
        String usernamePropertyName = identity + "Username";
        String passwordPropertyName = identity + "Password";
        CredentialsProviderFactory.assertRequiredValuesPresent(identity, Arrays.asList(usernamePropertyName, passwordPropertyName), gradleProperties);

        String usernameValue = gradleProperties.find(usernamePropertyName);
        String passwordValue = gradleProperties.find(passwordPropertyName);
        return new DefaultPasswordCredentials(usernameValue, passwordValue);
    }

}

abstract class AwsCredentialsValueSource implements ValueSource<AwsCredentials, AwsCredentialsValueSource.Parameters> {

    private final GradleProperties gradleProperties;

    public interface Parameters extends ValueSourceParameters {
        Property<String> getIdentity();
    }

    @Inject
    public AwsCredentialsValueSource(GradleProperties gradleProperties) {
        this.gradleProperties = gradleProperties;
    }

    @Nullable
    @Override
    public AwsCredentials obtain() {
        String identity = getParameters().getIdentity().get();
        String accessKeyPropertyName = identity + "AccessKey";
        String secretKeyPropertyName = identity + "SecretKey";
        String sessionTokenPropertyName = identity + "SessionToken";
        CredentialsProviderFactory.assertRequiredValuesPresent(getParameters().getIdentity().get(), Arrays.asList(accessKeyPropertyName, secretKeyPropertyName), gradleProperties);

        String accessKey = gradleProperties.find(accessKeyPropertyName);
        String secretKey = gradleProperties.find(secretKeyPropertyName);
        String sessionToken = gradleProperties.find(sessionTokenPropertyName);

        AwsCredentials credentials = new DefaultAwsCredentials();
        credentials.setAccessKey(accessKey);
        credentials.setSecretKey(secretKey);
        credentials.setSessionToken(sessionToken);
        return credentials;
    }

}

