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
package com.example.ide;

import com.example.ide.proto.IdeProjectModel;
import com.google.protobuf.Any;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * A tooling model builder contributed by the "IDE" plugin. It produces a protobuf message the
 * Gradle runtime knows nothing about, packs it into a {@link Any} using the plugin's own bundled
 * protobuf-java, and returns the raw {@code byte[]}. Gradle carries those bytes opaquely; the
 * daemon-side gRPC layer forwards them inside {@code ModelResponse.model_any} without ever
 * referencing the {@link IdeProjectModel} class or a shared protobuf runtime.
 */
public class IdeProjectModelBuilder implements ToolingModelBuilder {

    // The model name the client asks for; matches the Java type name of the model by convention.
    public static final String MODEL_NAME = "com.example.ide.IdeProjectModel";

    @Override
    public boolean canBuild(String modelName) {
        return MODEL_NAME.equals(modelName);
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        List<String> pluginIds = new ArrayList<>();
        for (Plugin<?> plugin : project.getPlugins()) {
            pluginIds.add(plugin.getClass().getName());
        }
        IdeProjectModel model = IdeProjectModel.newBuilder()
            .setProjectPath(project.getPath())
            .setProjectName(project.getName())
            .setBuildDir(project.getLayout().getBuildDirectory().getAsFile().get().getAbsolutePath())
            .addAllPluginIds(pluginIds)
            .build();
        return Any.pack(model).toByteArray();
    }
}
