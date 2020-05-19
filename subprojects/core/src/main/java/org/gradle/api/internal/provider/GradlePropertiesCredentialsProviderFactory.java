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

import org.gradle.api.Task;
import org.gradle.api.credentials.AwsCredentials;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.credentials.DefaultAwsCredentials;
import org.gradle.internal.credentials.DefaultPasswordCredentials;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class GradlePropertiesCredentialsProviderFactory implements CredentialsProviderFactory, TaskExecutionGraphListener {

    private final GradleProperties gradleProperties;

    private final Map<String, PasswordCredentialsProvider> passwordProviders = new HashMap<>();
    private final Map<String, AwsCredentialsProvider> awsProviders = new HashMap<>();

    public GradlePropertiesCredentialsProviderFactory(GradleProperties gradleProperties) {
        this.gradleProperties = gradleProperties;
    }

    @Override
    public <T extends Credentials> Provider<T> provideCredentials(Class<T> credentialsType, String identity) {
        validateIdentity(identity);

        if (PasswordCredentials.class.isAssignableFrom(credentialsType)) {
            return (Provider<T>) passwordProviders.computeIfAbsent(identity, PasswordCredentialsProvider::new);
        }
        if (AwsCredentials.class.isAssignableFrom(credentialsType)) {
            return (Provider<T>) awsProviders.computeIfAbsent(identity, AwsCredentialsProvider::new);
        }

        throw new IllegalArgumentException(String.format("Unsupported credentials type: %s", credentialsType));
    }

    @Override
    public <T extends Credentials> Provider<T> provideCredentials(Class<T> credentialsType, Supplier<String> identity) {
        return new DefaultProvider<>(() -> provideCredentials(credentialsType, identity.get()).get());
    }

    @Override
    public void graphPopulated(TaskExecutionGraph graph) {
        if (!passwordProviders.isEmpty() || !awsProviders.isEmpty()) {
            graph.getAllTasks().stream().map(Task::getInputs).forEach(TaskInputs::getProperties);
            passwordProviders.values().stream().filter(CredentialsProvider::valueRequested).forEach(AbstractMinimalProvider::get);
            awsProviders.values().stream().filter(CredentialsProvider::valueRequested).forEach(AbstractMinimalProvider::get);
        }
    }

    private void validateIdentity(@Nullable String identity) {
        if (identity == null || identity.isEmpty() || !identity.chars().allMatch(Character::isLetterOrDigit)) {
            throw new IllegalArgumentException("Identity may contain only letters and digits, received: " + identity);
        }
    }

    private abstract class CredentialsProvider<T extends Credentials> extends AbstractMinimalProvider<T> {
        private final Class<T> type;
        private final DisplayName displayName;

        private boolean valueRequested = false;

        protected CredentialsProvider(Class<T> type, DisplayName displayName) {
            this.type = type;
            this.displayName = displayName;
        }

        abstract List<String> requiredProperties();

        abstract T buildValue();

        @Override
        protected DisplayName getDisplayName() {
            return displayName;
        }

        @Nullable
        @Override
        public Class<T> getType() {
            return type;
        }

        boolean valueRequested() {
            return valueRequested;
        }

        @Override
        protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
            this.valueRequested = true;

            Value<T> missing = validateProperties();
            if (missing != null) {
                return missing;
            }
            return Value.of(buildValue());
        }

        @Nullable
        private Value<T> validateProperties() {
            List<DisplayName> missingProperties = new ArrayList<>();
            for (String property : requiredProperties()) {
                if (gradleProperties.find(property) == null) {
                    missingProperties.add(describeProperty(property));
                }
            }
            if (!missingProperties.isEmpty()) {
                Value<T> missing = Value.missing();
                for (DisplayName missingProperty : missingProperties) {
                    missing = missing.pushWhenMissing(missingProperty);
                }
                return missing;
            }
            return null;
        }

        private DisplayName describeProperty(String property) {
            return Describables.of(String.format("Gradle property '%s'", property));
        }
    }

    private class PasswordCredentialsProvider extends CredentialsProvider<PasswordCredentials> {

        private final String usernameProperty;
        private final String passwordProperty;

        PasswordCredentialsProvider(String identity) {
            super(PasswordCredentials.class, Describables.of("username and password provider"));
            this.usernameProperty = identity + "Username";
            this.passwordProperty = identity + "Password";
        }

        @Override
        List<String> requiredProperties() {
            return Arrays.asList(usernameProperty, passwordProperty);
        }

        @Override
        PasswordCredentials buildValue() {
            return new DefaultPasswordCredentials(gradleProperties.find(usernameProperty), gradleProperties.find(passwordProperty));
        }

    }

    private class AwsCredentialsProvider extends CredentialsProvider<AwsCredentials> {

        private final String accessKeyProperty;
        private final String secretKeyProperty;
        private final String sessionTokenProperty;

        AwsCredentialsProvider(String identity) {
            super(AwsCredentials.class, Describables.of("AWS credentials provider"));
            this.accessKeyProperty = identity + "AccessKey";
            this.secretKeyProperty = identity + "SecretKey";
            this.sessionTokenProperty = identity + "SessionToken";
        }

        @Override
        List<String> requiredProperties() {
            return Arrays.asList(accessKeyProperty, secretKeyProperty);
        }

        @Override
        AwsCredentials buildValue() {
            AwsCredentials credentials = new DefaultAwsCredentials();
            credentials.setAccessKey(gradleProperties.find(accessKeyProperty));
            credentials.setSecretKey(gradleProperties.find(secretKeyProperty));
            credentials.setSessionToken(gradleProperties.find(sessionTokenProperty));
            return credentials;
        }

    }

}
