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

package org.gradle.configuration;

import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.code.DefaultUserCodeSource;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.code.UserCodeApplicationId;
import org.gradle.internal.code.UserCodeSource;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.resource.ResourceLocation;
import org.gradle.internal.resource.TextResource;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.net.URI;

/**
 * A decorating {@link ScriptPlugin} that wraps the apply() logic in a build operation,
 * enabling better tracking and debugging of script plugin application.
 */
public class BuildOperationScriptPlugin implements ScriptPlugin {

    private static final ApplyScriptPluginBuildOperationType.Result OPERATION_RESULT = new ApplyScriptPluginBuildOperationType.Result() {};

    private final ScriptPlugin delegatePlugin;
    private final BuildOperationRunner buildOperationRunner;
    private final UserCodeApplicationContext userCodeApplicationContext;

    public BuildOperationScriptPlugin(ScriptPlugin delegatePlugin, BuildOperationRunner buildOperationRunner, UserCodeApplicationContext userCodeApplicationContext) {
        this.delegatePlugin = delegatePlugin;
        this.buildOperationRunner = buildOperationRunner;
        this.userCodeApplicationContext = userCodeApplicationContext;
    }

    @Override
    public ScriptSource getSource() {
        return delegatePlugin.getSource();
    }

    @Override
    public void apply(final Object target) {
        TextResource resource = getSource().getResource();

        if (resource.isContentCached() && resource.getHasEmptyContent()) {
            // No build operation wrapping needed if the script has no content
            delegatePlugin.apply(target);
        } else {
            UserCodeSource source = new DefaultUserCodeSource(getSource().getShortDisplayName(), null);
            userCodeApplicationContext.apply(source, userCodeApplicationId ->
                buildOperationRunner.run(new ApplyScriptPluginOperation(target, userCodeApplicationId))
            );
        }
    }

    /**
     * RunnableBuildOperation for applying a script plugin to a target.
     */
    private class ApplyScriptPluginOperation implements RunnableBuildOperation {

        private final Object target;
        private final UserCodeApplicationId applicationId;

        public ApplyScriptPluginOperation(Object target, UserCodeApplicationId applicationId) {
            this.target = target;
            this.applicationId = applicationId;
        }

        @Override
        public void run(BuildOperationContext context) {
            delegatePlugin.apply(target);
            context.setResult(OPERATION_RESULT);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            ScriptSource source = getSource();
            ResourceLocation resourceLocation = source.getResource().getLocation();
            File file = resourceLocation.getFile();
            String name = "Apply " + source.getShortDisplayName();
            String displayName = name + " to " + target;

            return BuildOperationDescriptor.displayName(displayName)
                .name(name)
                .details(new OperationDetails(file, resourceLocation, ConfigurationTargetIdentifier.of(target), applicationId));
        }
    }

    /**
     * Build operation metadata for script plugin application.
     */
    private static class OperationDetails implements ApplyScriptPluginBuildOperationType.Details {

        private final File file;
        private final ResourceLocation resourceLocation;
        private final ConfigurationTargetIdentifier identifier;
        private final UserCodeApplicationId applicationId;

        private OperationDetails(File file, ResourceLocation resourceLocation, @Nullable ConfigurationTargetIdentifier identifier, UserCodeApplicationId applicationId) {
            this.file = file;
            this.resourceLocation = resourceLocation;
            this.identifier = identifier;
            this.applicationId = applicationId;
        }

        @Override
        @Nullable
        public String getFile() {
            return file != null ? file.getAbsolutePath() : null;
        }

        @Override
        @Nullable
        public String getUri() {
            if (file == null) {
                URI uri = resourceLocation.getURI();
                return uri != null ? uri.toASCIIString() : null;
            }
            return null;
        }

        @Override
        public String getTargetType() {
            return identifier != null ? identifier.getTargetType().label : null;
        }

        @Override
        @Nullable
        public String getTargetPath() {
            return identifier != null ? identifier.getTargetPath() : null;
        }

        @Override
        public String getBuildPath() {
            return identifier != null ? identifier.getBuildPath() : null;
        }

        @Override
        public long getApplicationId() {
            return applicationId.longValue();
        }
    }
}
