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

package org.gradle.internal.enterprise.impl;

import org.gradle.internal.buildtree.BuildModelParameters;
import org.gradle.internal.enterprise.GradleEnterprisePluginCheckInResult;
import org.gradle.internal.enterprise.GradleEnterprisePluginCheckInService;
import org.gradle.internal.enterprise.GradleEnterprisePluginMetadata;
import org.gradle.internal.enterprise.GradleEnterprisePluginServiceFactory;
import org.gradle.internal.enterprise.GradleEnterprisePluginServiceRef;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager;
import org.gradle.util.internal.VersionNumber;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

import static org.gradle.internal.enterprise.impl.legacy.DevelocityPluginCompatibility.getUnsupportedPluginMessage;
import static org.gradle.internal.enterprise.impl.legacy.DevelocityPluginCompatibility.getUnsupportedWithIsolatedProjectsMessage;
import static org.gradle.internal.enterprise.impl.legacy.DevelocityPluginCompatibility.isUnsupportedPluginVersion;
import static org.gradle.internal.enterprise.impl.legacy.DevelocityPluginCompatibility.isUnsupportedWithIsolatedProjects;

public class DefaultGradleEnterprisePluginCheckInService implements GradleEnterprisePluginCheckInService {

    private final GradleEnterprisePluginManager manager;
    private final DefaultGradleEnterprisePluginAdapterFactory pluginAdapterFactory;
    private final boolean isIsolatedProjectsEnabled;

    public DefaultGradleEnterprisePluginCheckInService(
        BuildModelParameters buildModelParameters,
        GradleEnterprisePluginManager manager,
        DefaultGradleEnterprisePluginAdapterFactory pluginAdapterFactory
    ) {
        this.manager = manager;
        this.pluginAdapterFactory = pluginAdapterFactory;
        this.isIsolatedProjectsEnabled = buildModelParameters.isIsolatedProjects();
    }

    // Used just for testing
    public static final String UNSUPPORTED_TOGGLE = "org.gradle.internal.unsupported-enterprise-plugin";
    public static final String UNSUPPORTED_TOGGLE_MESSAGE = "Enterprise plugin unsupported due to secret toggle";

    private static final String DISABLE_TEST_ACCELERATION_PROPERTY = "gradle.internal.testacceleration.disableImplicitApplication";

    @Override
    public GradleEnterprisePluginCheckInResult checkIn(GradleEnterprisePluginMetadata pluginMetadata, GradleEnterprisePluginServiceFactory serviceFactory) {
        if (Boolean.getBoolean(UNSUPPORTED_TOGGLE)) {
            return checkInUnsupportedResult(UNSUPPORTED_TOGGLE_MESSAGE);
        }

        String pluginVersion = pluginMetadata.getVersion();
        VersionNumber pluginBaseVersion = VersionNumber.parse(pluginVersion).getBaseVersion();

        if (isUnsupportedPluginVersion(pluginBaseVersion)) {
            return checkInUnsupportedResult(getUnsupportedPluginMessage(pluginVersion));
        }

        if (isIsolatedProjectsEnabled && isUnsupportedWithIsolatedProjects(pluginBaseVersion)) {
            // Until GE plugin 3.14, Test Acceleration is applied even if the check-in returns an "unsupported" result.
            // We have to disable it explicitly, because it is not compatible with isolated projects.
            System.setProperty(DISABLE_TEST_ACCELERATION_PROPERTY, "true");

            return checkInUnsupportedResult(getUnsupportedWithIsolatedProjectsMessage(pluginVersion));
        }

        DefaultGradleEnterprisePluginAdapter adapter = pluginAdapterFactory.create(serviceFactory);
        GradleEnterprisePluginServiceRef ref = adapter.getPluginServiceRef();
        manager.registerAdapter(adapter);
        return checkInResult(null, () -> ref);
    }

    private GradleEnterprisePluginCheckInResult checkInUnsupportedResult(String unsupportedMessage) {
        manager.unsupported();
        return checkInResult(unsupportedMessage, () -> {
            throw new IllegalStateException();
        });
    }

    private static GradleEnterprisePluginCheckInResult checkInResult(@Nullable String unsupportedMessage, Supplier<GradleEnterprisePluginServiceRef> pluginServiceRefSupplier) {
        return new GradleEnterprisePluginCheckInResult() {
            @Nullable
            @Override
            public String getUnsupportedMessage() {
                return unsupportedMessage;
            }

            @Override
            public GradleEnterprisePluginServiceRef getPluginServiceRef() {
                return pluginServiceRefSupplier.get();
            }
        };
    }
}
