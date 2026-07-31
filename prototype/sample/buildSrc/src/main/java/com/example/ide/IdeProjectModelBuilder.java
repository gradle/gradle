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

import com.example.ide.proto.IdeModelQuery;
import com.example.ide.proto.IdeProjectModel;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * A tooling model builder contributed by the "IDE" plugin. It produces a protobuf message the Gradle
 * runtime knows nothing about; the daemon-side gRPC layer packs it into a {@code google.protobuf.Any}
 * for native clients, and the classic Tooling API adapts it onto the client's view interface. Both
 * work because the plugin and the daemon share one {@code com.google.protobuf.Message} class
 * (protobuf-java is exported to plugins - see DefaultGradleApiSpecProvider).
 *
 * <p>It is a {@link ParameterizedToolingModelBuilder} so a client can tailor the model with a typed
 * parameter. The parameter travels as the bytes of an {@code Any} the builder unpacks into its own
 * {@link IdeModelQuery} - the request-side mirror of the opaque-Any model result.</p>
 */
public class IdeProjectModelBuilder implements ParameterizedToolingModelBuilder<IdeModelParameter> {

    // The stable model identifier both clients ask for. The native gRPC client sends this string;
    // the JVM Tooling API client requests getModel(<its interface named this>.class). It is the
    // protobuf message's full name, which is also the Any type-url the native client unpacks.
    public static final String MODEL_NAME = "com.example.ide.IdeProjectModel";

    @Override
    public boolean canBuild(String modelName) {
        return MODEL_NAME.equals(modelName);
    }

    @Override
    public Class<IdeModelParameter> getParameterType() {
        return IdeModelParameter.class;
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        // No parameter (the JVM Tooling API parity client, and a native client that sent none):
        // return the full model.
        return build(project, true);
    }

    @Override
    public Object buildAll(String modelName, IdeModelParameter parameter, Project project) {
        return build(project, includePlugins(parameter));
    }

    private static boolean includePlugins(IdeModelParameter parameter) {
        byte[] bytes = parameter.getParameterBytes();
        if (bytes == null || bytes.length == 0) {
            return true;
        }
        try {
            return Any.parseFrom(bytes).unpack(IdeModelQuery.class).getIncludePlugins();
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Model parameter is not a valid IdeModelQuery Any", e);
        }
    }

    private static IdeProjectModel build(Project project, boolean includePlugins) {
        IdeProjectModel.Builder model = IdeProjectModel.newBuilder()
            .setProjectPath(project.getPath())
            .setProjectName(project.getName())
            .setBuildDir(project.getLayout().getBuildDirectory().getAsFile().get().getAbsolutePath());
        if (includePlugins) {
            List<String> pluginIds = new ArrayList<>();
            for (Plugin<?> plugin : project.getPlugins()) {
                pluginIds.add(plugin.getClass().getName());
            }
            model.addAllPluginIds(pluginIds);
        }
        return model.build();
    }
}
