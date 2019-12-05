/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import com.google.common.base.MoreObjects;
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.internal.ExecuteDomainObjectCollectionCallbackBuildOperationType;
import org.gradle.api.internal.plugins.ApplyPluginBuildOperationType;
import org.gradle.configuration.ApplyScriptPluginBuildOperationType;
import org.gradle.configuration.internal.ExecuteListenerBuildOperationType;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.internal.protocol.events.InternalBinaryPluginIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalPluginIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalScriptPluginIdentifier;
import org.gradle.internal.build.event.types.DefaultBinaryPluginIdentifier;
import org.gradle.internal.build.event.types.DefaultScriptPluginIdentifier;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

class PluginApplicationTracker implements BuildOperationListener {

    private static final String PROJECT_TARGET_TYPE = "project";

    private final Map<OperationIdentifier, PluginApplication> runningPluginApplications = new ConcurrentHashMap<>();
    private final Map<Long, PluginApplication> pluginApplicationRegistry = new ConcurrentHashMap<>();
    private final BuildOperationParentTracker parentTracker;

    PluginApplicationTracker(BuildOperationParentTracker parentTracker) {
        this.parentTracker = parentTracker;
    }

    @Nullable
    public PluginApplication getRunningPluginApplication(OperationIdentifier id) {
        return runningPluginApplications.get(id);
    }

    public boolean hasRunningPluginApplication(OperationIdentifier id, Predicate<? super PluginApplication> predicate) {
        return parentTracker.findClosestMatchingAncestor(id, parent -> {
            PluginApplication pluginApplication = runningPluginApplications.get(parent);
            return pluginApplication != null && predicate.test(pluginApplication);
        }) != null;
    }

    @Nullable
    public PluginApplication findRunningPluginApplication(OperationIdentifier id) {
        return parentTracker.findClosestExistingAncestor(id, runningPluginApplications::get);
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        if (buildOperation.getDetails() instanceof ApplyPluginBuildOperationType.Details) {
            ApplyPluginBuildOperationType.Details details = (ApplyPluginBuildOperationType.Details) buildOperation.getDetails();
            createAndTrack(buildOperation, details.getTargetType(), details.getApplicationId(), () -> toBinaryPluginIdentifier(details));
        } else if (buildOperation.getDetails() instanceof ApplyScriptPluginBuildOperationType.Details) {
            ApplyScriptPluginBuildOperationType.Details details = (ApplyScriptPluginBuildOperationType.Details) buildOperation.getDetails();
            createAndTrack(buildOperation, details.getTargetType(), details.getApplicationId(), () -> toScriptPluginIdentifier(details));
        } else if (buildOperation.getDetails() instanceof ExecuteListenerBuildOperationType.Details) {
            ExecuteListenerBuildOperationType.Details details = (ExecuteListenerBuildOperationType.Details) buildOperation.getDetails();
            lookupAndTrack(buildOperation, details.getApplicationId());
        } else if (buildOperation.getDetails() instanceof ExecuteDomainObjectCollectionCallbackBuildOperationType.Details) {
            ExecuteDomainObjectCollectionCallbackBuildOperationType.Details details = (ExecuteDomainObjectCollectionCallbackBuildOperationType.Details) buildOperation.getDetails();
            lookupAndTrack(buildOperation, details.getApplicationId());
        }
    }

    private void createAndTrack(BuildOperationDescriptor buildOperation, String targetType, long applicationId, Supplier<InternalPluginIdentifier> pluginSupplier) {
        if (PROJECT_TARGET_TYPE.equals(targetType)) {
            InternalPluginIdentifier plugin = pluginSupplier.get();
            if (plugin != null) {
                PluginApplication pluginApplication = new PluginApplication(applicationId, plugin);
                pluginApplicationRegistry.put(applicationId, pluginApplication);
                track(buildOperation, pluginApplication);
            }
        }
    }

    private void lookupAndTrack(BuildOperationDescriptor buildOperation, long applicationId) {
        PluginApplication pluginApplication = pluginApplicationRegistry.get(applicationId);
        if (pluginApplication != null) {
            track(buildOperation, pluginApplication);
        }
    }

    private void track(BuildOperationDescriptor buildOperation, PluginApplication pluginApplication) {
        runningPluginApplications.put(buildOperation.getId(), pluginApplication);
    }

    @Override
    public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        runningPluginApplications.remove(buildOperation.getId());
    }

    private InternalBinaryPluginIdentifier toBinaryPluginIdentifier(ApplyPluginBuildOperationType.Details details) {
        String className = details.getPluginClass().getName();
        String pluginId = details.getPluginId();
        String displayName = MoreObjects.firstNonNull(pluginId, className);
        return new DefaultBinaryPluginIdentifier(displayName, className, pluginId);
    }

    private InternalScriptPluginIdentifier toScriptPluginIdentifier(ApplyScriptPluginBuildOperationType.Details details) {
        String fileString = details.getFile();
        if (fileString != null) {
            File file = new File(fileString);
            return new DefaultScriptPluginIdentifier(file.getName(), file.toURI());
        }
        String uriString = details.getUri();
        if (uriString != null) {
            URI uri = URI.create(uriString);
            return new DefaultScriptPluginIdentifier(FilenameUtils.getName(uri.getPath()), uri);
        }
        return null;
    }

    static class PluginApplication {

        private final long applicationId;
        private final InternalPluginIdentifier plugin;

        PluginApplication(long applicationId, InternalPluginIdentifier plugin) {
            this.applicationId = applicationId;
            this.plugin = plugin;
        }

        public long getApplicationId() {
            return applicationId;
        }

        public InternalPluginIdentifier getPlugin() {
            return plugin;
        }

    }

}
