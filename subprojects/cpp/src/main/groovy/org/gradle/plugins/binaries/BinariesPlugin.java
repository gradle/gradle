/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.binaries;

import org.gradle.api.Plugin;
import org.gradle.api.internal.FactoryNamedDomainObjectContainer;
import org.gradle.api.internal.Instantiator;
import org.gradle.api.internal.ReflectiveNamedDomainObjectFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.plugins.binaries.model.Executable;
import org.gradle.plugins.binaries.model.Library;
import org.gradle.plugins.binaries.model.internal.DefaultCompilerRegistry;
import org.gradle.plugins.binaries.model.internal.DefaultExecutable;
import org.gradle.plugins.binaries.model.internal.DefaultLibrary;

/**
 * temp plugin, not sure what will provide the binaries container and model elements
 */
public class BinariesPlugin implements Plugin<ProjectInternal> {

    public void apply(final ProjectInternal project) {
        project.getPlugins().apply(BasePlugin.class);

        Instantiator instantiator = project.getServices().get(Instantiator.class);
        project.getExtensions().add("executables", instantiator.newInstance(
                FactoryNamedDomainObjectContainer.class,
                Executable.class,
                instantiator,
                new ReflectiveNamedDomainObjectFactory<Executable>(DefaultExecutable.class, project)
        ));
        project.getExtensions().add("libraries", instantiator.newInstance(
                FactoryNamedDomainObjectContainer.class,
                Library.class,
                instantiator,
                new ReflectiveNamedDomainObjectFactory<Library>(DefaultLibrary.class, project)
        ));

        project.getExtensions().add("compilers", instantiator.newInstance(
                DefaultCompilerRegistry.class,
                instantiator
        ));
    }

}