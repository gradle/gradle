/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.autoapply.AutoAppliedGradleEnterprisePlugin;
import org.gradle.plugin.use.internal.PluginRequestApplicator;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import static org.gradle.plugin.management.internal.PluginRequestInternal.Origin.AUTO_APPLIED;

public class GradleEnterprisePluginAutoApplicationListener implements PluginRequestApplicator.PluginApplicationListener {

    private final GradleEnterprisePluginAutoAppliedStatus status;

    @Inject
    public GradleEnterprisePluginAutoApplicationListener(GradleEnterprisePluginAutoAppliedStatus status) {
        this.status = status;
    }

    @Override
    public void pluginApplied(@Nonnull PluginRequestInternal pluginRequest) {
        if (pluginRequest.getOrigin() == AUTO_APPLIED && AutoAppliedGradleEnterprisePlugin.ID.equals(pluginRequest.getId())) {
            status.markAsAutoApplied();
        }
    }
}
