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
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.credentials.DefaultPasswordCredentials;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GradlePropertiesCredentialsProviderFactory implements CredentialsProviderFactory {

    private final GradleProperties gradleProperties;
    private final TaskExecutionGraph taskExecutionGraph;

    private boolean credentialsBeingEvaluated = false;

    private final Map<String, PasswordCredentialsProvider> passwordProviders = new HashMap<>();

    public GradlePropertiesCredentialsProviderFactory(GradleProperties gradleProperties, TaskExecutionGraph taskExecutionGraph) {
        this.gradleProperties = gradleProperties;
        this.taskExecutionGraph = taskExecutionGraph;
    }

    @Override
    public Provider<PasswordCredentials> usernameAndPassword(String identity) {
        validateIdentity(identity);
        registerCredentialsEvaluator();
        return passwordProviders.computeIfAbsent(identity, PasswordCredentialsProvider::new);
    }

    private void registerCredentialsEvaluator() {
        if (!credentialsBeingEvaluated) {
            taskExecutionGraph.whenReady(graph -> {
                graph.getAllTasks().stream().map(Task::getInputs).forEach(TaskInputs::getProperties);
                passwordProviders.values().stream().filter(p -> p.valueRequested).forEach(AbstractMinimalProvider::get);
            });
            credentialsBeingEvaluated = true;
        }
    }

    private void validateIdentity(@Nullable String identity) {
        if (identity == null || identity.isEmpty() || !identity.chars().allMatch(Character::isLetterOrDigit)) {
            throw new IllegalArgumentException("Identity may contain only letters and digits, received: " + identity);
        }
    }

    private class PasswordCredentialsProvider extends AbstractMinimalProvider<PasswordCredentials> {

        private final String usernameProperty;
        private final String passwordProperty;

        private boolean valueRequested = false;

        public PasswordCredentialsProvider(String identity) {
            this.usernameProperty = identity + "Username";
            this.passwordProperty = identity + "Password";
        }

        @Override
        protected DisplayName getDisplayName() {
            return Describables.of("username and password provider");
        }

        @Override
        protected Value<? extends PasswordCredentials> calculateOwnValue(ValueConsumer consumer) {
            this.valueRequested = true;
            String username = gradleProperties.find(usernameProperty);
            String password = gradleProperties.find(passwordProperty);
            List<DisplayName> missingProperties = new ArrayList<>();
            if (username == null) {
                missingProperties.add(describeProperty(usernameProperty));
            }
            if (password == null) {
                missingProperties.add(describeProperty(passwordProperty));
            }

            if (missingProperties.isEmpty()) {
                return Value.of(new DefaultPasswordCredentials(username, password));
            }

            Value<PasswordCredentials> missing = Value.missing();
            for (DisplayName missingProperty : missingProperties) {
                missing = missing.pushWhenMissing(missingProperty);
            }
            return missing;
        }

        @Nullable
        @Override
        public Class<PasswordCredentials> getType() {
            return PasswordCredentials.class;
        }

        private DisplayName describeProperty(String property) {
            return Describables.of(String.format("Gradle property '%s'", property));
        }
    }

}

