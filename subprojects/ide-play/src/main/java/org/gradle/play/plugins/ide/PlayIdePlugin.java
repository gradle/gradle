/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.play.plugins.ide;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.AppliedPlugin;
import org.gradle.play.plugins.ide.internal.PlayIdeaPlugin;

/**
 * Plugin for configuring IDE plugins when the project uses the Play Framework component support.
 *
 * <p>NOTE: This currently supports configuring the 'idea' plugin only.</p>
 */
@Incubating
public class PlayIdePlugin implements Plugin<Project> {
    @Override
    public void apply(final Project project) {
        project.getPluginManager().withPlugin("idea", new Action<AppliedPlugin>() {
            @Override
            public void execute(AppliedPlugin appliedPlugin) {
                project.getPluginManager().apply(PlayIdeaPlugin.class);
            }
        });
        // TODO: Configure 'eclipse' projects too
    }
}
