/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling;

import org.gradle.api.Project;
import org.gradle.launcher.cli.internal.HelpRenderer;
import org.gradle.tooling.internal.build.DefaultHelp;
import org.gradle.tooling.model.build.Help;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class HelpBuilder implements ToolingModelBuilder {
    public HelpBuilder() { }

    @Override
    public boolean canBuild(String modelName) {
        return Help.class.getName().equals(modelName);
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        return new DefaultHelp(
            HelpRenderer.render(null, true)
        );
    }
}
