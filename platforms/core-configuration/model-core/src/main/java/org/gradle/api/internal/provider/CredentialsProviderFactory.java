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
import org.gradle.api.credentials.AwsCredentials;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.credentials.HttpHeaderCredentials;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.credentials.DefaultAwsCredentials;
import org.gradle.internal.credentials.DefaultHttpHeaderCredentials;
import org.gradle.internal.credentials.DefaultPasswordCredentials;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CredentialsProviderFactory implements TaskExecutionGraphListener {

    private final ProviderFactory providerFactory;
    private final ObjectFactory objectFactory;

    private final Map<String, Provider<PasswordCredentials>> passwordProviders = new ConcurrentHashMap<>();
    private final Map<String, Provider<AwsCredentials>> awsProviders = new ConcurrentHashMap<>();
    private final Map<String, Provider<HttpHeaderCredentials>> httpHeaderProviders = new ConcurrentHashMap<>();

    private final Set<String> missingProviderErrors = ConcurrentHashMap.newKeySet();

    public CredentialsProviderFactory(ProviderFactory providerFactory, @Nullable ObjectFactory objectFactory) {
        this.providerFactory = providerFactory;
        this.objectFactory = objectFactory;
    }

    @SuppressWarnings("unchecked")
    public <T extends Credentials> Provider<T> provide(Class<T> credentialsType, String identity) {
        validateIdentity(identity);
        if (objectFactory == null) {
            throw new UnsupportedOperationException();
        }

        if (PasswordCredentials.class.isAssignableFrom(credentialsType)) {
            return (Provider<T>) passwordProviders.computeIfAbsent(identity, id -> evaluateAtConfigurationTime(new PasswordCredentialsProvider(id)));
        }
        if (AwsCredentials.class.isAssignableFrom(credentialsType)) {
            return (Provider<T>) awsProviders.computeIfAbsent(identity, id -> evaluateAtConfigurationTime(new AwsCredentialsProvider(id)));
        }
        if (HttpHeaderCredentials.class.isAssignableFrom(credentialsType)) {
            return (Provider<T>) httpHeaderProviders.computeIfAbsent(identity, id -> evaluateAtConfigurationTime(new HttpHeaderCredentialsProvider(id)));
        }

        throw new IllegalArgumentException(String.format("Unsupported credentials type: %s", credentialsType));
    }

    public <T extends Credentials> Provider<T> provide(Class<T> credentialsType, Provider<String> identity) {
        return evaluateAtConfigurationTime(() -> provide(credentialsType, identity.get()).get());
    }

    @Override
    public void graphPopulated(TaskExecutionGraph graph) {
        if (!missingProviderErrors.isEmpty()) {
            throw new ProjectConfigurationException("Credentials required for this build could not be resolved.",
                missingProviderErrors.stream().map(MissingValueException::new).collect(Collectors.toList()));
        }
    }

    private abstract class CredentialsProvider<T extends Credentials> implements Callable<T> {
        private final String identity;

        private final Set<String> missingProperties = new LinkedHashSet<>();

        CredentialsProvider(String identity) {
            this.identity = identity;
        }

        @Nullable
        String getRequiredProperty(String property) {
            String identityProperty = identityProperty(property);
            Provider<String> propertyProvider = providerFactory.gradleProperty(identityProperty);
            if (!propertyProvider.isPresent()) {
                missingProperties.add(identityProperty);
            }
            return propertyProvider.getOrNull();
        }

        @Nullable
        String getOptionalProperty(String property) {
            String identityProperty = identityProperty(property);
            return providerFactory.gradleProperty(identityProperty).getOrNull();
        }

        void assertRequiredValuesPresent() {
            if (!missingProperties.isEmpty()) {
                TreeFormatter errorBuilder = new TreeFormatter();
                errorBuilder.node("The following Gradle properties are missing for '").append(identity).append("' credentials");
                errorBuilder.startChildren();
                for (String missingProperty : missingProperties) {
                    errorBuilder.node(missingProperty);
                }
                errorBuilder.endChildren();
                missingProperties.clear();
                throw new MissingValueException(errorBuilder.toString());
            }
            if (objectFactory == null) {
                throw new UnsupportedOperationException("Cannot create credentials in this context since ObjectFactory is not available");
            }
        }

        private String identityProperty(String property) {
            return identity + property;
        }
    }

    private class PasswordCredentialsProvider extends CredentialsProvider<PasswordCredentials> {

        PasswordCredentialsProvider(String identity) {
            super(identity);
        }

        @Override
        public synchronized PasswordCredentials call() {
            String username = getRequiredProperty("Username");
            String password = getRequiredProperty("Password");
            assertRequiredValuesPresent();

            DefaultPasswordCredentials credentials = objectFactory.newInstance(DefaultPasswordCredentials.class);
            credentials.setUsername(username);
            credentials.setPassword(password);
            return credentials;
        }
    }

    private class AwsCredentialsProvider extends CredentialsProvider<AwsCredentials> {

        AwsCredentialsProvider(String identity) {
            super(identity);
        }

        @Override
        public synchronized AwsCredentials call() {
            String accessKey = getRequiredProperty("AccessKey");
            String secretKey = getRequiredProperty("SecretKey");
            assertRequiredValuesPresent();

            AwsCredentials credentials = objectFactory.newInstance(DefaultAwsCredentials.class);
            credentials.setAccessKey(accessKey);
            credentials.setSecretKey(secretKey);
            credentials.setSessionToken(getOptionalProperty("SessionToken"));
            return credentials;
        }
    }

    private class HttpHeaderCredentialsProvider extends CredentialsProvider<HttpHeaderCredentials> {

        HttpHeaderCredentialsProvider(String identity) {
            super(identity);
        }

        @Override
        public synchronized HttpHeaderCredentials call() {
            String name = getRequiredProperty("AuthHeaderName");
            String value = getRequiredProperty("AuthHeaderValue");
            assertRequiredValuesPresent();

            HttpHeaderCredentials credentials = objectFactory.newInstance(DefaultHttpHeaderCredentials.class);
            credentials.setName(name);
            credentials.setValue(value);
            return credentials;
        }
    }

    private static void validateIdentity(@Nullable String identity) {
        if (identity == null || identity.isEmpty() || !identity.chars().allMatch(Character::isLetterOrDigit)) {
            throw new IllegalArgumentException("Identity may contain only letters and digits, received: " + identity);
        }
    }

    private <T extends Credentials> Provider<T> evaluateAtConfigurationTime(Callable<T> provider) {
        return new InterceptingProvider<>(provider);
    }

    private class InterceptingProvider<T> extends DefaultProvider<T> {

        public InterceptingProvider(Callable<? extends T> value) {
            super(value);
        }

        @Override
        public ValueProducer getProducer() {
            calculatePresence(ValueConsumer.IgnoreUnsafeRead);
            return super.getProducer();
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            try {
                return super.calculatePresence(consumer);
            } catch (MissingValueException e) {
                missingProviderErrors.add(e.getMessage());
                if (consumer == ValueConsumer.IgnoreUnsafeRead) {
                    return false;
                }
                throw e;
            }
        }

        @Override
        protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
            try {
                return super.calculateOwnValue(consumer);
            } catch (MissingValueException e) {
                missingProviderErrors.add(e.getMessage());
                throw e;
            }
        }
    }
}
