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
package org.gradle.tooling.internal.provider.action;

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.jspecify.annotations.NullMarked;

import java.io.File;

/**
 * Queries a tooling model for the native gRPC tooling API prototype.
 *
 * <p>Unlike {@link BuildModelAction}, which always builds the model for the default (root) project,
 * this action carries an optional logical project path so the native client can target a specific
 * project the way a Tooling API {@code BuildController.getModel(project, type)} call does - without
 * the client pointing its connection at the subproject's physical directory. When the project path
 * is empty the model is built for the default target, matching {@link BuildModelAction}.</p>
 *
 * <p>It is only ever constructed and run in-process inside the daemon (by the gRPC service), so it
 * never crosses the classic Tooling API wire and needs no {@code BuildActionSerializer} support.</p>
 */
@NullMarked
public class GrpcModelQueryAction extends SubscribableBuildAction {
    private final StartParameterInternal startParameter;
    private final String modelName;
    private final File buildRootDir;
    private final String projectPath;
    private final byte[] parameterBytes;

    public GrpcModelQueryAction(StartParameterInternal startParameter, String modelName, File buildRootDir, String projectPath, byte[] parameterBytes, BuildEventSubscriptions clientSubscriptions) {
        super(clientSubscriptions);
        this.startParameter = startParameter;
        this.modelName = modelName;
        this.buildRootDir = buildRootDir;
        this.projectPath = projectPath;
        this.parameterBytes = parameterBytes;
    }

    @Override
    public StartParameterInternal getStartParameter() {
        return startParameter;
    }

    public String getModelName() {
        return modelName;
    }

    public File getBuildRootDir() {
        return buildRootDir;
    }

    /**
     * The logical path of the project to target (e.g. {@code :app}), or an empty string to build the
     * model for the default target.
     */
    public String getProjectPath() {
        return projectPath;
    }

    /**
     * The serialized {@code google.protobuf.Any} parameter for a parameterized model builder, or an
     * empty array when the client sent no parameter. Gradle carries these bytes opaquely; the plugin's
     * builder unpacks them.
     */
    public byte[] getParameterBytes() {
        return parameterBytes;
    }

    @Override
    public boolean isRunTasks() {
        return false;
    }

    @Override
    public boolean isCreateModel() {
        return true;
    }
}
