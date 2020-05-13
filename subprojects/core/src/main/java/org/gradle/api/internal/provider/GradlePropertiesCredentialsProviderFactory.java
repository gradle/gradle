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

import org.gradle.api.credentials.Credentials;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.credentials.DefaultPasswordCredentials;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class GradlePropertiesCredentialsProviderFactory implements CredentialsProviderFactory {

    private final GradleProperties gradleProperties;

    public GradlePropertiesCredentialsProviderFactory(GradleProperties gradleProperties) {
        this.gradleProperties = gradleProperties;
    }

    @Override
    public Provider<Credentials> usernameAndPassword(String identity) {
        validateIdentity(identity);
        return new PasswordCredentialsProvider(identity);
    }

    private void validateIdentity(@Nullable String identity) {
        if (identity == null || identity.isEmpty() || !identity.chars().allMatch(Character::isLetterOrDigit)) {
            throw new IllegalArgumentException("Identity may contain only letters and digits, received: " + identity);
        }
    }

    private class PasswordCredentialsProvider extends AbstractMinimalProvider<Credentials> {

        private final String usernameProperty;
        private final String passwordProperty;

        public PasswordCredentialsProvider(String identity) {
            this.usernameProperty = identity + "Username";
            this.passwordProperty = identity + "Password";
        }

        @Override
        protected DisplayName getDisplayName() {
            return Describables.of("username and password provider");
        }

        @Override
        protected Value<? extends Credentials> calculateOwnValue(ValueConsumer consumer) {
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

            Value<Credentials> missing = Value.missing();
            for (DisplayName missingProperty : missingProperties) {
                missing = missing.pushWhenMissing(missingProperty);
            }
            return missing;
        }

        @Nullable
        @Override
        public Class<Credentials> getType() {
            return Credentials.class;
        }

        private DisplayName describeProperty(String property) {
            return Describables.of(String.format("Gradle property '%s'", property));
        }
    }
}

