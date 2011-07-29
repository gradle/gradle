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
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.plugins.BasePlugin;

import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.FactoryNamedDomainObjectContainer;
import org.gradle.api.internal.project.ProjectInternal;

import org.gradle.plugins.binaries.model.Binary;
import org.gradle.plugins.binaries.model.internal.DefaultBinary;

import org.gradle.plugins.cpp.gcc.GppCompileSpec;

/**
 * temp plugin, not sure what will provide the binaries container and model elements
 */
public class BinariesPlugin implements Plugin<ProjectInternal> {

    public void apply(final ProjectInternal project) {
        project.getPlugins().apply(BasePlugin.class);
        
        ClassGenerator classGenerator = project.getServices().get(ClassGenerator.class);
        project.getExtensions().add("binaries", classGenerator.newInstance(
                FactoryNamedDomainObjectContainer.class,
                DefaultBinary.class,
                classGenerator,
                new NamedDomainObjectFactory<Binary>() {
                    public Binary create(String name) {
                        
                        // note: specs will come from some kind of registry/factory so there won't
                        // be a link on a compile impl here
                        return new DefaultBinary(name, new GppCompileSpec(name, project));
                    }
                }
        ));
    }

}