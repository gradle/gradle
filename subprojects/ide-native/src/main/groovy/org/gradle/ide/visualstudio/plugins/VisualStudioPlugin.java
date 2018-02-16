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

package org.gradle.ide.visualstudio.plugins;

import org.gradle.api.*;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.ide.visualstudio.VisualStudioExtension;
import org.gradle.ide.visualstudio.VisualStudioRootExtension;
import org.gradle.ide.visualstudio.internal.CppApplicationVisualStudioTargetBinary;
import org.gradle.ide.visualstudio.internal.CppSharedLibraryVisualStudioTargetBinary;
import org.gradle.ide.visualstudio.internal.CppStaticLibraryVisualStudioTargetBinary;
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioExtension;
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioRootExtension;
import org.gradle.ide.visualstudio.internal.VisualStudioExtensionInternal;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.language.cpp.CppLibrary;
import org.gradle.language.cpp.CppSharedLibrary;
import org.gradle.language.cpp.CppStaticLibrary;
import org.gradle.language.cpp.plugins.CppBasePlugin;
import org.gradle.nativeplatform.plugins.NativeComponentModelPlugin;
import org.gradle.plugins.ide.internal.IdePlugin;

import javax.inject.Inject;


/**
 * A plugin for creating a Visual Studio solution for a gradle project.
 */
@Incubating
public class VisualStudioPlugin extends IdePlugin {
    private static final String LIFECYCLE_TASK_NAME = "visualStudio";

    private final Instantiator instantiator;
    private final FileResolver fileResolver;

    @Inject
    public VisualStudioPlugin(Instantiator instantiator, FileResolver fileResolver) {
        this.instantiator = instantiator;
        this.fileResolver = fileResolver;
    }

    @Override
    protected String getLifecycleTaskName() {
        return LIFECYCLE_TASK_NAME;
    }

    @Override
    protected void onApply(final Project target) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);

        final VisualStudioExtensionInternal extension;
        if (isRoot()) {
            extension = (VisualStudioExtensionInternal) project.getExtensions().create(VisualStudioRootExtension.class, "visualStudio", DefaultVisualStudioRootExtension.class, project.getName(), instantiator, fileResolver, this);
            getLifecycleTask().dependsOn(((VisualStudioRootExtension)extension).getSolution());
        } else {
            extension = (VisualStudioExtensionInternal) project.getExtensions().create(VisualStudioExtension.class, "visualStudio", DefaultVisualStudioExtension.class, instantiator, fileResolver, this);
            getLifecycleTask().dependsOn(extension.getProjects());
        }

        project.getPlugins().withType(CppBasePlugin.class).all(new Action<CppBasePlugin>() {
            @Override
            public void execute(CppBasePlugin cppBasePlugin) {
                project.getComponents().withType(CppApplication.class).all(new Action<CppApplication>() {
                    @Override
                    public void execute(final CppApplication cppApplication) {
                        cppApplication.getBinaries().whenElementFinalized(new Action<CppBinary>() {
                            @Override
                            public void execute(CppBinary cppBinary) {
                                extension.getProjectRegistry().addProjectConfiguration(new CppApplicationVisualStudioTargetBinary(target, cppApplication, (CppExecutable) cppBinary));
                            }
                        });
                    }
                });
                project.getComponents().withType(CppLibrary.class).all(new Action<CppLibrary>() {
                    @Override
                    public void execute(final CppLibrary cppLibrary) {
                        cppLibrary.getBinaries().whenElementFinalized(new Action<CppBinary>() {
                            @Override
                            public void execute(CppBinary cppBinary) {
                                if (cppBinary instanceof CppSharedLibrary) {
                                    extension.getProjectRegistry().addProjectConfiguration(new CppSharedLibraryVisualStudioTargetBinary(target, cppLibrary, (CppSharedLibrary) cppBinary));
                                }
                                if (cppBinary instanceof CppStaticLibrary) {
                                    extension.getProjectRegistry().addProjectConfiguration(new CppStaticLibraryVisualStudioTargetBinary(project, cppLibrary, (CppStaticLibrary) cppBinary));
                                }
                            }
                        });
                    }
                });
            }
        });

        // TODO: Figure out how to conditionally apply VisualStudioPluginRules but ensure that rules are still fired in subprojects
        project.getPluginManager().apply(NativeComponentModelPlugin.class);
        project.getPluginManager().apply(VisualStudioPluginRules.class);
    }
}
