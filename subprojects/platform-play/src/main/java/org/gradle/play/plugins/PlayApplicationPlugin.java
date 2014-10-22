/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.play.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.jvm.plugins.JvmComponentPlugin;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.ComponentTypeBuilder;
import org.gradle.play.DefaultPlayApplicationSpec;
import org.gradle.play.PlayApplicationSpec;

public class PlayApplicationPlugin implements Plugin<Project> {

    public void apply(Project project) {
        project.getPlugins().apply(JvmComponentPlugin.class);
    }

    @RuleSource
    static class Rules {
        @ComponentType
        void register(ComponentTypeBuilder<PlayApplicationSpec> builder) {
            builder.defaultImplementation(DefaultPlayApplicationSpec.class);
        }

    }
}
