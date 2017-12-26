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
import org.gradle.api.Project;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.component.SoftwareComponentContainer;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.ComponentWithBinaries;
import org.gradle.language.ComponentWithInstallation;
import org.gradle.language.ComponentWithLinkFile;
import org.gradle.language.ComponentWithRuntimeFile;
import org.gradle.language.ProductionComponent;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import java.util.concurrent.Callable;

/**
 * A common base plugin for the native plugins.
 *
 * <p>Expects plugins to register the native components in the {@link Project#getComponents()} container, and defines a number of rules that act on these components to configure them.</p>
 *
 * <ul>
 *
 * <li>Configures the {@value LifecycleBasePlugin#ASSEMBLE_TASK_NAME} task to build the development binary of the {@code main} component, if present. Expects the main component to be of type {@link ProductionComponent}.</li>
 *
 * </ul>
 *
 * @since 4.5
 */
@Incubating
public class NativeBasePlugin implements Plugin<ProjectInternal> {
    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);

        // Register each child of each component
        final SoftwareComponentContainer components = project.getComponents();
        components.withType(ComponentWithBinaries.class, new Action<ComponentWithBinaries>() {
            @Override
            public void execute(ComponentWithBinaries component) {
                component.getBinaries().whenElementKnown(new Action<SoftwareComponent>() {
                    @Override
                    public void execute(SoftwareComponent binary) {
                        components.add(binary);
                    }
                });
            }
        });

        final TaskContainer tasks = project.getTasks();
        tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(new Callable<Object>() {
            @Override
            public Object call() {
                // Assemble the development binary of the main component by default
                ProductionComponent productionComponent = project.getComponents().withType(ProductionComponent.class).findByName("main");
                if (productionComponent == null) {
                    return null;
                }

                // Determine which output to produce at development time. Should introduce an abstraction for this
                SoftwareComponent developmentBinary = productionComponent.getDevelopmentBinary().get();
                if (developmentBinary instanceof ComponentWithInstallation) {
                    return ((ComponentWithInstallation) developmentBinary).getInstallDirectory();
                }
                if (developmentBinary instanceof ComponentWithRuntimeFile) {
                    return ((ComponentWithRuntimeFile) developmentBinary).getRuntimeFile();
                }
                if (developmentBinary instanceof ComponentWithLinkFile) {
                    return ((ComponentWithLinkFile) developmentBinary).getLinkFile();
                }
                return null;
            }
        });
    }

}
