/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.code;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.internal.build.event.types.DefaultBinaryPluginIdentifier;
import org.gradle.internal.build.event.types.DefaultPluginApplicationResult;
import org.gradle.internal.build.event.types.DefaultScriptPluginIdentifier;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.tooling.internal.protocol.events.InternalBinaryPluginIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalPluginIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalProjectConfigurationResult;
import org.gradle.tooling.internal.protocol.events.InternalScriptPluginIdentifier;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks all user code applications that have been executed for a single build invocation.
 */
@ServiceScope(Scope.CrossBuildSession.class)
public class UserCodeApplicationRegistry {

    /**
     * All known user code applications, mapped by the identity path of the project that they were applied to.
     */
    private final ConcurrentHashMap<Path, List<UserCodeApplicationContext.Application>> applications = new ConcurrentHashMap<>();

    /**
     * Register a new user code application against the given project.
     */
    public void register(Path projectIdentityPath, UserCodeApplicationContext.Application application) {
        applications.computeIfAbsent(projectIdentityPath, k -> Collections.synchronizedList(new ArrayList<>())).add(application);
    }

    public Map<Path, List<UserCodeApplicationContext.Application>> consumeApplications() {
        Map<Path, List<UserCodeApplicationContext.Application>> copy = ImmutableMap.copyOf(this.applications);
        this.applications.clear();
        return copy;
    }

    /**
     * For each code application against the given project, return an object describing the time spent applying that code.
     */
    public List<InternalProjectConfigurationResult.InternalPluginApplicationResult> getApplicationResultsForProject(Path projectIdentityPath) {
        List<UserCodeApplicationContext.Application> projectApplications =
            this.applications.getOrDefault(projectIdentityPath, Collections.emptyList());

        ImmutableList.Builder<InternalProjectConfigurationResult.InternalPluginApplicationResult> result =
            ImmutableList.builderWithExpectedSize(projectApplications.size());

        for (UserCodeApplicationContext.Application application : projectApplications) {
            InternalPluginIdentifier pluginId = toInternalPluginId(application.getSource());
            if (pluginId != null) {
                result.add(new DefaultPluginApplicationResult(
                    pluginId,
                    Duration.ofNanos(application.getTotalDurationNs())
                ));
            }
        }

        return result.build();
    }

    public static @Nullable InternalPluginIdentifier toInternalPluginId(@Nullable UserCodeSource source) {
        if (source instanceof UserCodeSource.Binary) {
            return toBinaryPluginIdentifier((UserCodeSource.Binary) source);
        } else if (source instanceof UserCodeSource.Script) {
            UserCodeSource.Script scriptSource = (UserCodeSource.Script) source;
            if (scriptSource.getUri() != null) {
                return toScriptPluginIdentifier(scriptSource);
            }
        }

        return null;
    }

    private static InternalBinaryPluginIdentifier toBinaryPluginIdentifier(UserCodeSource.Binary source) {
        String displayName = source.getPluginId() != null ? source.getPluginId() : source.getClassName();
        return new DefaultBinaryPluginIdentifier(
            displayName,
            source.getClassName(),
            source.getPluginId()
        );
    }

    private static InternalScriptPluginIdentifier toScriptPluginIdentifier(UserCodeSource.Script source) {
        return new DefaultScriptPluginIdentifier(
            source.getDisplayName().getDisplayName(),
            source.getUri()
        );
    }

}
