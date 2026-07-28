/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.testretry.internal.config;

import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.SetProperty;
import org.gradle.testretry.TestRetryTaskExtension;
import org.gradle.util.GradleVersion;

import java.util.Set;
import java.util.concurrent.Callable;

import static java.util.Collections.emptySet;
import static org.gradle.testretry.internal.config.TestTaskConfigurer.supportsPropertyConventions;

public final class TestRetryTaskExtensionAdapter implements TestRetryTaskExtensionAccessor {

    // for testing only
    public static final String SIMULATE_NOT_RETRYABLE_PROPERTY = "__org_gradle_testretry_simulate_not_retryable";

    private static final int DEFAULT_MAX_RETRIES = 0;
    private static final int DEFAULT_MAX_FAILURES = 0;
    private static final boolean DEFAULT_FAIL_ON_PASSED_AFTER_RETRY = false;
    private static final boolean DEFAULT_FAIL_ON_SKIPPED_AFTER_RETRY = true;

    private final ProviderFactory providerFactory;
    private final TestRetryTaskExtension extension;
    private final boolean simulateNotRetryableTest;
    private final boolean useConventions;

    public TestRetryTaskExtensionAdapter(
        ProviderFactory providerFactory,
        TestRetryTaskExtension extension,
        GradleVersion gradleVersion
    ) {
        this.providerFactory = providerFactory;
        this.extension = extension;
        this.simulateNotRetryableTest = Boolean.getBoolean(SIMULATE_NOT_RETRYABLE_PROPERTY);
        this.useConventions = supportsPropertyConventions(gradleVersion);

        initialize(extension, this.useConventions);
    }

    private static void initialize(TestRetryTaskExtension extension, boolean gradle51OrLater) {
        TestRetryTaskExtension.Filter filter = extension.getFilter();
        TestRetryTaskExtension.ClassRetryCriteria classRetry = extension.getClassRetry();
        if (gradle51OrLater) {
            extension.getMaxRetries().convention(DEFAULT_MAX_RETRIES);
            extension.getMaxFailures().convention(DEFAULT_MAX_FAILURES);
            extension.getFailOnPassedAfterRetry().convention(DEFAULT_FAIL_ON_PASSED_AFTER_RETRY);
            extension.getFailOnSkippedAfterRetry().convention(DEFAULT_FAIL_ON_SKIPPED_AFTER_RETRY);
            filter.getIncludeClasses().convention(emptySet());
            filter.getIncludeAnnotationClasses().convention(emptySet());
            filter.getExcludeClasses().convention(emptySet());
            filter.getExcludeAnnotationClasses().convention(emptySet());
            classRetry.getIncludeClasses().convention(emptySet());
            classRetry.getIncludeAnnotationClasses().convention(emptySet());
        } else {
            // https://github.com/gradle/gradle/issues/7485
            filter.getIncludeClasses().empty();
            filter.getIncludeAnnotationClasses().empty();
            filter.getExcludeClasses().empty();
            filter.getExcludeAnnotationClasses().empty();
            classRetry.getIncludeClasses().empty();
            classRetry.getIncludeAnnotationClasses().empty();
        }
    }

    Callable<Provider<Boolean>> getFailOnPassedAfterRetryInput() {
        if (useConventions) {
            return extension::getFailOnPassedAfterRetry;
        } else {
            return () -> {
                if (extension.getFailOnPassedAfterRetry().isPresent()) {
                    return extension.getFailOnPassedAfterRetry();
                } else {
                    return providerFactory.provider(TestRetryTaskExtensionAdapter.this::getFailOnPassedAfterRetry);
                }
            };
        }
    }

    Callable<Provider<Boolean>> getFailOnSkippedAfterRetryInput() {
        if (useConventions) {
            return extension::getFailOnSkippedAfterRetry;
        } else {
            return () -> {
                if (extension.getFailOnSkippedAfterRetry().isPresent()) {
                    return extension.getFailOnSkippedAfterRetry();
                } else {
                    return providerFactory.provider(TestRetryTaskExtensionAdapter.this::getFailOnSkippedAfterRetry);
                }
            };
        }
    }

    @Override
    public boolean getFailOnPassedAfterRetry() {
        return read(extension.getFailOnPassedAfterRetry(), DEFAULT_FAIL_ON_PASSED_AFTER_RETRY);
    }

    @Override
    public boolean getFailOnSkippedAfterRetry() {
        return read(extension.getFailOnSkippedAfterRetry(), DEFAULT_FAIL_ON_SKIPPED_AFTER_RETRY);
    }

    @Override
    public int getMaxRetries() {
        return read(extension.getMaxRetries(), DEFAULT_MAX_RETRIES);
    }

    @Override
    public int getMaxFailures() {
        return read(extension.getMaxFailures(), DEFAULT_MAX_FAILURES);
    }

    @Override
    public Set<String> getIncludeClasses() {
        return read(extension.getFilter().getIncludeClasses(), emptySet());
    }

    @Override
    public Set<String> getIncludeAnnotationClasses() {
        return read(extension.getFilter().getIncludeAnnotationClasses(), emptySet());
    }

    @Override
    public Set<String> getExcludeClasses() {
        return read(extension.getFilter().getExcludeClasses(), emptySet());
    }

    @Override
    public Set<String> getExcludeAnnotationClasses() {
        return read(extension.getFilter().getExcludeAnnotationClasses(), emptySet());
    }

    @Override
    public Set<String> getClassRetryIncludeClasses() {
        return read(extension.getClassRetry().getIncludeClasses(), emptySet());
    }

    @Override
    public Set<String> getClassRetryIncludeAnnotationClasses() {
        return read(extension.getClassRetry().getIncludeAnnotationClasses(), emptySet());
    }

    @Override
    public boolean getSimulateNotRetryableTest() {
        return simulateNotRetryableTest;
    }

    private <T> T read(Property<T> property, T defaultValue) {
        return useConventions ? property.get() : property.getOrElse(defaultValue);
    }

    private <T> Set<T> read(SetProperty<T> property, Set<T> defaultValue) {
        return useConventions ? property.get() : property.getOrElse(defaultValue);
    }

}
