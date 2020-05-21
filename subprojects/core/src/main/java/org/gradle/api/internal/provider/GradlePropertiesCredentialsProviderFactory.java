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

import org.gradle.api.credentials.AwsCredentials;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.api.provider.Provider;
import org.gradle.internal.credentials.DefaultAwsCredentials;
import org.gradle.internal.credentials.DefaultPasswordCredentials;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public class GradlePropertiesCredentialsProviderFactory implements CredentialsProviderFactory {

    private final GradleProperties gradleProperties;

    private final Map<String, Provider<PasswordCredentials>> passwordProviders = new HashMap<>();
    private final Map<String, Provider<AwsCredentials>> awsProviders = new HashMap<>();

    public GradlePropertiesCredentialsProviderFactory(GradleProperties gradleProperties) {
        this.gradleProperties = gradleProperties;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Credentials> Provider<T> provideCredentials(Class<T> credentialsType, String identity) {
        validateIdentity(identity);

        if (PasswordCredentials.class.isAssignableFrom(credentialsType)) {
            return (Provider<T>) passwordProviders.computeIfAbsent(identity, id -> evaluateAtConfigurationTime(new PasswordCredentialsProvider(id)));
        }
        if (AwsCredentials.class.isAssignableFrom(credentialsType)) {
            return (Provider<T>) awsProviders.computeIfAbsent(identity, id -> evaluateAtConfigurationTime(new AwsCredentialsProvider(id)));
        }

        throw new IllegalArgumentException(String.format("Unsupported credentials type: %s", credentialsType));
    }

    @Override
    public <T extends Credentials> Provider<T> provideCredentials(Class<T> credentialsType, Supplier<String> identity) {
        return evaluateAtConfigurationTime(() -> provideCredentials(credentialsType, identity.get()).get());
    }

    private abstract class CredentialsProvider<T extends Credentials> implements Callable<T> {
        private final String identity;

        private final List<String> missingProperties = new ArrayList<>();

        CredentialsProvider(String identity) {
            this.identity = identity;
        }

        @Nullable
        String getRequiredProperty(String property) {
            String identityProperty = identityProperty(property);
            String value = gradleProperties.find(identityProperty);
            if (value == null) {
                missingProperties.add(identityProperty);
            }
            return value;
        }

        @Nullable
        String getProperty(String property) {
            String identityProperty = identityProperty(property);
            return gradleProperties.find(identityProperty);
        }

        void assertRequiredValuesPresent() {
            if (!missingProperties.isEmpty()) {
                TreeFormatter errorBuilder = new TreeFormatter();
                errorBuilder.node("Credentials for '").append(identity).append("' required for this build could not be found.");
                errorBuilder.node("The following Gradle properties are missing");
                errorBuilder.startChildren();
                for (String missingProperty : missingProperties) {
                    errorBuilder.node(missingProperty);
                }
                errorBuilder.endChildren();
                throw new MissingValueException(errorBuilder.toString());
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
        public PasswordCredentials call() {
            String username = getRequiredProperty("Username");
            String password = getRequiredProperty("Password");
            assertRequiredValuesPresent();

            return new DefaultPasswordCredentials(username, password);
        }
    }

    private class AwsCredentialsProvider extends CredentialsProvider<AwsCredentials> {

        AwsCredentialsProvider(String identity) {
            super(identity);
        }

        @Override
        public AwsCredentials call() {
            String accessKey = getRequiredProperty("AccessKey");
            String secretKey = getRequiredProperty("SecretKey");
            assertRequiredValuesPresent();

            AwsCredentials credentials = new DefaultAwsCredentials();
            credentials.setAccessKey(accessKey);
            credentials.setSecretKey(secretKey);
            credentials.setSessionToken(getProperty("SessionToken"));
            return credentials;
        }
    }

    private static void validateIdentity(@Nullable String identity) {
        if (identity == null || identity.isEmpty() || !identity.chars().allMatch(Character::isLetterOrDigit)) {
            throw new IllegalArgumentException("Identity may contain only letters and digits, received: " + identity);
        }
    }

    private static <T extends Credentials> Provider<T> evaluateAtConfigurationTime(Callable<T> provider) {
        return new DefaultProvider<>(provider).orElse(Providers.notDefined());
    }
}
