/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.api.UncheckedIOException;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.internal.consumer.TestExecutionRequest;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.model.internal.Exceptions;
import org.gradle.util.GradleVersion;

import java.io.IOException;

public class VersionControlConsumerConnection implements ConsumerConnection {
    private static final String UNSUPPORTED_RUN_MESSAGE = "Support for builds using Gradle versions older than 1.2 was removed in tooling API version 3.0. You are currently using Gradle version %s. You should upgrade your Gradle build to use Gradle 1.2 or later.";
    private static final String DEPRECATION_WARNING = "Support for %s older than %s is deprecated and will be removed in 5.0.%s You should upgrade your %s.";
    public static final GradleVersion MIN_PROVIDER_VERSION = GradleVersion.version("1.2");
    public static final GradleVersion MIN_LTS_PROVIDER_VERSION = GradleVersion.version("2.6");

    private final ConsumerConnection delegate;
    private final String displayName;
    private final GradleVersion providerVersion;
    private final boolean deprecated;
    private final boolean unsupported;

    public VersionControlConsumerConnection(ConsumerConnection delegate, VersionDetails versionDetails) {
        this(delegate, versionDetails.getVersion(), delegate.getDisplayName());
    }

    public VersionControlConsumerConnection(ConnectionVersion4 delegate) {
        this(null, delegate.getMetaData().getVersion(), delegate.getMetaData().getDisplayName());
    }

    private VersionControlConsumerConnection(ConsumerConnection connection, String providerVersion, String displayName) {
        this.delegate = connection;
        this.providerVersion = GradleVersion.version(providerVersion);
        this.unsupported = this.providerVersion.compareTo(MIN_PROVIDER_VERSION) < 0;
        this.deprecated = this.providerVersion.compareTo(MIN_LTS_PROVIDER_VERSION) < 0;
        this.displayName = displayName;
    }


    @Override
    public void stop() {
        if (delegate != null) {
            delegate.stop();
        }
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public <T> T run(Class<T> type, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        checkVersion(new UnsupportedVersionException(String.format(UNSUPPORTED_RUN_MESSAGE, providerVersion.getVersion())));
        checkDeprecation(operationParameters);
        return delegate.run(type, operationParameters);
    }

    @Override
    public <T> T run(BuildAction<T> action, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        checkVersion(Exceptions.unsupportedFeature(operationParameters.getEntryPointName(), providerVersion.getVersion(), "1.8"));
        checkDeprecation(operationParameters);
        return delegate.run(action, operationParameters);
    }

    @Override
    public void runTests(TestExecutionRequest testExecutionRequest, ConsumerOperationParameters operationParameters) {
        checkVersion(Exceptions.unsupportedFeature(operationParameters.getEntryPointName(), providerVersion.getVersion(), "2.6"));
        checkDeprecation(operationParameters);
        delegate.runTests(testExecutionRequest, operationParameters);
    }

    private void checkVersion(RuntimeException exception) {
        if (unsupported) {
            throw exception;
        }
    }

    private void checkDeprecation(ConsumerOperationParameters parameters) {
        if (deprecated && parameters.getStandardError() != null) {
            try {
                parameters.getStandardError().write(createDeprecationMessage(providerVersion, MIN_LTS_PROVIDER_VERSION, "Gradle").getBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static String createCurrentVersionMessage(GradleVersion currentVersion) {
        if (currentVersion == null) {
            return "";
        } else {
            return String.format(" You are currently using %s.", currentVersion.getVersion());
        }
    }

    private static String createDeprecationMessage(GradleVersion currentVersion, GradleVersion minLtsVersion, String description) {
        return String.format(DEPRECATION_WARNING, description, minLtsVersion.getVersion(), createCurrentVersionMessage(currentVersion), description);
    }
}
