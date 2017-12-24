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

package org.gradle.language.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.language.ComponentWithBinaries;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

/**
 * A common base plugin for the native plugins
 *
 * @since 4.5
 */
@Incubating
public class NativeBasePlugin implements Plugin<ProjectInternal> {
    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);

        project.getComponents().withType(ComponentWithBinaries.class, new Action<ComponentWithBinaries>() {
            @Override
            public void execute(ComponentWithBinaries component) {
                component.getBinaries().whenElementKnown(new Action<SoftwareComponent>() {
                    @Override
                    public void execute(SoftwareComponent binary) {
                        project.getComponents().add(binary);
                    }
                });
            }
        });
    }
}
