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
import org.gradle.api.credentials.AwsCredentials;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.WorkNodeAction;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.internal.Cast;
import org.gradle.internal.credentials.DefaultAwsCredentials;
import org.gradle.internal.credentials.DefaultPasswordCredentials;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class CredentialsProviderFactory {

    private final ProviderFactory providerFactory;

    public CredentialsProviderFactory(ProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    public <T extends Credentials> Provider<T> provide(Class<T> credentialsType, String identity) {
        return provide(credentialsType, providerFactory.provider(() -> identity));
    }

    public <T extends Credentials> Provider<T> provide(Class<T> credentialsType, Provider<String> identity) {
        if (PasswordCredentials.class.isAssignableFrom(credentialsType)) {
            return Cast.uncheckedCast(credentialsProvider(identity, PasswordCredentialsValueSource.class));
        }
        if (AwsCredentials.class.isAssignableFrom(credentialsType)) {
            return Cast.uncheckedCast(credentialsProvider(identity, AwsCredentialsValueSource.class));
        }

        throw new IllegalArgumentException(String.format("Unsupported credentials type: %s", credentialsType));
    }

    private <T extends Credentials> Provider<T> credentialsProvider(Provider<String> identity, Class<? extends CredentialsValueSource<T>> valueSourceClass) {
        Provider<T> provider = providerFactory.of(
            valueSourceClass,
            spec -> spec.getParameters().getIdentity().set(identity)
        );
        return new InterceptingProvider<>(provider);
    }

    private static class InterceptingProvider<T extends Credentials> extends AbstractMinimalProvider<T> {

        private final ProviderInternal<T> delegate;

        public InterceptingProvider(Provider<T> delegate) {
            this.delegate = (ProviderInternal<T>) delegate;
        }

        @Override
        public ValueProducer getProducer() {
            return delegate.getProducer().plus(ValueProducer.nodeAction(new ResolveCredentialsWorkNodeAction(this)));
        }

        @Nullable
        @Override
        public Class<T> getType() {
            return delegate.getType();
        }

        @Override
        protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
            return delegate.calculateValue(consumer);
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            try {
                return super.calculatePresence(consumer);
            } catch (MissingValueException e) {
                if (consumer == ValueConsumer.IgnoreUnsafeRead) {
                    return false;
                }
                throw e;
            }
        }

        @Override
        public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
            return delegate.calculateExecutionTimeValue();
        }

    }

    public static class ResolveCredentialsWorkNodeAction implements WorkNodeAction {
        private final Provider<?> provider;

        public ResolveCredentialsWorkNodeAction(Provider<?> provider) {
            this.provider = provider;
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

abstract class CredentialsValueSource<T extends Credentials> implements ValueSource<T, CredentialsValueSource.Parameters> {

    public interface Parameters extends ValueSourceParameters {
        Property<String> getIdentity();
    }

    private final List<String> missingProperties = new ArrayList<>();

    @Inject
    protected abstract GradleProperties getGradleProperties();

    String getValidatedIdentity() {
        String identity = getParameters().getIdentity().getOrNull();
        if (identity == null || identity.isEmpty() || !identity.chars().allMatch(Character::isLetterOrDigit)) {
            throw new IllegalArgumentException("Identity may contain only letters and digits, received: " + identity);
        }
        return identity;
    }

    void assertRequiredValuesPresent() {
        String identity = getParameters().getIdentity().get();
        if (!missingProperties.isEmpty()) {
            TreeFormatter errorBuilder = new TreeFormatter();
            errorBuilder.node("The following Gradle properties are missing for '").append(identity).append("' credentials");
            errorBuilder.startChildren();
            for (String missingProperty : missingProperties) {
                errorBuilder.node(missingProperty);
            }
            errorBuilder.endChildren();
            throw new MissingValueException(errorBuilder.toString());
        }
    }

    @Nullable
    String getProperty(String propertyName) {
        return getGradleProperties().find(propertyName);
    }

    @Nullable
    String getRequiredProperty(String propertyName) {
        String propertyValue = getProperty(propertyName);
        if (propertyValue == null) {
            missingProperties.add(propertyName);
        }
        return propertyValue;
    }

}

abstract class PasswordCredentialsValueSource extends CredentialsValueSource<PasswordCredentials> {

    public PasswordCredentialsValueSource() {
    }

    @Nullable
    @Override
    public PasswordCredentials obtain() {
        String identity = getValidatedIdentity();

        String usernameValue = getRequiredProperty(identity + "Username");
        String passwordValue = getRequiredProperty(identity + "Password");
        assertRequiredValuesPresent();

        return new DefaultPasswordCredentials(usernameValue, passwordValue);
    }
}

abstract class AwsCredentialsValueSource extends CredentialsValueSource<AwsCredentials> {

    public AwsCredentialsValueSource() {
    }

    @Nullable
    @Override
    public AwsCredentials obtain() {
        String identity = getValidatedIdentity();

        String accessKey = getRequiredProperty(identity + "AccessKey");
        String secretKey = getRequiredProperty(identity + "SecretKey");
        String sessionToken = getProperty(identity + "SessionToken");
        assertRequiredValuesPresent();

        AwsCredentials credentials = new DefaultAwsCredentials();
        credentials.setAccessKey(accessKey);
        credentials.setSecretKey(secretKey);
        credentials.setSessionToken(sessionToken);
        return credentials;
    }
}
