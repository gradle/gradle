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

import org.gradle.internal.enterprise.GradleEnterprisePluginCheckInResult;
import org.gradle.internal.enterprise.GradleEnterprisePluginCheckInService;
import org.gradle.internal.enterprise.GradleEnterprisePluginMetadata;
import org.gradle.internal.enterprise.GradleEnterprisePluginServiceFactory;
import org.gradle.internal.enterprise.GradleEnterprisePluginServiceRef;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager;

import javax.annotation.Nullable;

public class DefautGradleEnterprisePluginCheckInService implements GradleEnterprisePluginCheckInService {

    private final GradleEnterprisePluginManager manager;
    private final DefaultGradleEnterprisePluginAdapter adapter;

    public DefautGradleEnterprisePluginCheckInService(
        GradleEnterprisePluginManager manager,
        DefaultGradleEnterprisePluginAdapter adapter
    ) {
        this.manager = manager;
        this.adapter = adapter;
    }

    // Used just for testing
    public static final String UNSUPPORTED_TOGGLE = "org.gradle.internal.unsupported-enterprise-plugin";
    public static final String UNSUPPORTED_TOGGLE_MESSAGE = "Enterprise plugin unsupported due to secret toggle";

    @Override
    public GradleEnterprisePluginCheckInResult checkIn(GradleEnterprisePluginMetadata pluginMetadata, GradleEnterprisePluginServiceFactory serviceFactory) {
        if (Boolean.getBoolean(UNSUPPORTED_TOGGLE)) {
            manager.unsupported();
            return new GradleEnterprisePluginCheckInResult() {
                @Override
                public String getUnsupportedMessage() {
                    return UNSUPPORTED_TOGGLE_MESSAGE;
                }

                @Override
                public GradleEnterprisePluginServiceRef getPluginServiceRef() {
                    throw new IllegalStateException();
                }
            };
        } else {
            GradleEnterprisePluginServiceRef ref = adapter.register(serviceFactory);
            manager.registerAdapter(adapter);
            return new GradleEnterprisePluginCheckInResult() {
                @Nullable
                @Override
                public String getUnsupportedMessage() {
                    return null;
                }

                @Override
                public GradleEnterprisePluginServiceRef getPluginServiceRef() {
                    return ref;
                }
            };
        }
    }

}
