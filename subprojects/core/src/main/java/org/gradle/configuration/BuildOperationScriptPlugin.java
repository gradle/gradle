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

import org.gradle.internal.code.DefaultUserCodeSource;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.code.UserCodeApplicationId;
import org.gradle.internal.code.UserCodeSource;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.resource.ResourceLocation;
import org.gradle.internal.resource.TextResource;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;

/**
 * A decorating {@link ScriptPlugin} implementation that delegates to a given
 * delegatee implementation, but wraps the apply() execution in a
 * {@link org.gradle.internal.operations.BuildOperation}.
 */
public class BuildOperationScriptPlugin implements ScriptPlugin {

    private final ScriptPlugin decorated;
    private final BuildOperationExecutor buildOperationExecutor;
    private final UserCodeApplicationContext userCodeApplicationContext;

    public BuildOperationScriptPlugin(ScriptPlugin decorated, BuildOperationExecutor buildOperationExecutor, UserCodeApplicationContext userCodeApplicationContext) {
        this.decorated = decorated;
        this.buildOperationExecutor = buildOperationExecutor;
        this.userCodeApplicationContext = userCodeApplicationContext;
    }

    @Override
    public ScriptSource getSource() {
        return decorated.getSource();
    }

    @Override
    public void apply(final Object target) {
        TextResource resource = getSource().getResource();
        if (resource.isContentCached() && resource.getHasEmptyContent()) {
            //no operation, if there is no script code provided
            decorated.apply(target);
        } else {
            UserCodeSource source = new DefaultUserCodeSource(getSource().getShortDisplayName(), false);
            userCodeApplicationContext.apply(source, userCodeApplicationId -> buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    decorated.apply(target);
                    context.setResult(OPERATION_RESULT);
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    final ScriptSource source = getSource();
                    final ResourceLocation resourceLocation = source.getResource().getLocation();
                    final File file = resourceLocation.getFile();
                    String name = "Apply " + source.getShortDisplayName();
                    final String displayName = name + " to " + target;

                    return BuildOperationDescriptor.displayName(displayName)
                        .name(name)
                        .details(new OperationDetails(file, resourceLocation, ConfigurationTargetIdentifier.of(target), userCodeApplicationId));
                }
            }));
        }
    }

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
            return file == null ? null : file.getAbsolutePath();
        }

        @Nullable
        @Override
        public String getUri() {
            if (file == null) {
                URI uri = resourceLocation.getURI();
                return uri == null ? null : uri.toASCIIString();
            } else {
                return null;
            }
        }

        @Override
        public String getTargetType() {
            return identifier == null ? null : identifier.getTargetType().label;
        }

        @Nullable
        @Override
        public String getTargetPath() {
            return identifier == null ? null : identifier.getTargetPath();
        }

        @Override
        public String getBuildPath() {
            return identifier == null ? null : identifier.getBuildPath();
        }

        @Override
        public long getApplicationId() {
            return applicationId.longValue();
        }
    }


    private static final ApplyScriptPluginBuildOperationType.Result OPERATION_RESULT = new ApplyScriptPluginBuildOperationType.Result() {
    };
}
