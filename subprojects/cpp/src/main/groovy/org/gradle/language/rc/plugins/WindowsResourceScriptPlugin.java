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
package org.gradle.language.rc.plugins;

import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.language.base.internal.LanguageRegistration;
import org.gradle.language.base.internal.LanguageRegistry;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.rc.WindowsResourceSet;
import org.gradle.language.rc.internal.DefaultWindowsResourceSet;
import org.gradle.nativebinaries.internal.ProjectNativeBinaryInternal;
import org.gradle.nativebinaries.language.internal.CreateSourceTransformTask;
import org.gradle.nativebinaries.language.internal.DefaultPreprocessingTool;
import org.gradle.nativebinaries.language.internal.WindowsResourcesCompileTaskConfig;
import org.gradle.runtime.base.BinaryContainer;

import java.util.Map;

/**
 * Adds core language support for Windows resource script files.
 */
@Incubating
public class WindowsResourceScriptPlugin implements Plugin<ProjectInternal> {

    public void apply(final ProjectInternal project) {
        WindowsResources language = new WindowsResources();

        project.getPlugins().apply(ComponentModelBasePlugin.class);
        project.getExtensions().getByType(LanguageRegistry.class).add(language);

        final CreateSourceTransformTask createRule = new CreateSourceTransformTask(language);
        BinaryContainer binaries = project.getExtensions().getByType(BinaryContainer.class);
        binaries.withType(ProjectNativeBinaryInternal.class).all(new Action<ProjectNativeBinaryInternal>() {
            public void execute(ProjectNativeBinaryInternal binary) {
                if (shouldProcessResources(binary)) {
                    createRule.createCompileTasksForBinary(project.getTasks(), binary);
                }
            }
        });
    }

    private boolean shouldProcessResources(ProjectNativeBinaryInternal binary) {
        return binary.getTargetPlatform().getOperatingSystem().isWindows();
    }

    private static class WindowsResources implements LanguageRegistration<WindowsResourceSet> {
        public String getName() {
            return "rc";
        }

        public Class<WindowsResourceSet> getSourceSetType() {
            return WindowsResourceSet.class;
        }

        public Class<? extends WindowsResourceSet> getSourceSetImplementation() {
            return DefaultWindowsResourceSet.class;
        }

        public Map<String, Class<?>> getBinaryTools() {
            Map<String, Class<?>> tools = Maps.newLinkedHashMap();
            tools.put("rcCompiler", DefaultPreprocessingTool.class);
            return tools;
        }

        public SourceTransformTaskConfig getTransformTask() {
            return new WindowsResourcesCompileTaskConfig();
        }
    }
}
