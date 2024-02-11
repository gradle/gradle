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

import org.gradle.StartParameter;
import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.buildevents.BuildStartedTime;
import org.gradle.internal.enterprise.GradleEnterprisePluginBuildState;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.scopeids.id.UserScopeId;
import org.gradle.internal.scopeids.id.WorkspaceScopeId;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.time.Clock;
import org.gradle.launcher.daemon.server.scaninfo.DaemonScanInfo;

@ServiceScope(Scopes.Gradle.class)
public class DefaultGradleEnterprisePluginBuildState implements GradleEnterprisePluginBuildState {

    private final Clock clock;
    private final BuildStartedTime buildStartedTime;
    private final BuildInvocationScopeId buildInvocationId;
    private final WorkspaceScopeId workspaceId;
    private final UserScopeId userId;
    private final GradleInternal gradle;

    public DefaultGradleEnterprisePluginBuildState(
        Clock clock,
        BuildStartedTime buildStartedTime,
        BuildInvocationScopeId buildInvocationId,
        WorkspaceScopeId workspaceId,
        UserScopeId userId,
        GradleInternal gradle
    ) {
        this.clock = clock;
        this.buildStartedTime = buildStartedTime;
        this.buildInvocationId = buildInvocationId;
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.gradle = gradle;
    }

    @Override
    public long getBuildStartedTime() {
        return buildStartedTime.getStartTime();
    }

    @Override
    public long getCurrentTime() {
        return clock.getCurrentTime();
    }

    @Override
    public String getBuildInvocationId() {
        return buildInvocationId.getId().asString();
    }

    @Override
    public String getWorkspaceId() {
        return workspaceId.getId().asString();
    }

    @Override
    public String getUserId() {
        return userId.getId().asString();
    }

    @Override
    public DaemonScanInfo getDaemonScanInfo() {
        return (DaemonScanInfo) gradle.getServices().find(DaemonScanInfo.class);
    }

    @Override
    public StartParameter getStartParameter() {
        return gradle.getStartParameter();
    }
}
