/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.language.java.internal;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.LocalComponentFactory;
import org.gradle.internal.component.local.model.DefaultLibraryComponentIdentifier;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetaData;
import org.gradle.internal.component.local.model.MutableLocalComponentMetaData;

public class DefaultJavaLocalComponentFactory implements LocalComponentFactory {

    @Override
    public boolean canConvert(Object source) {
        return source instanceof DefaultJavaSourceSetResolveContext;
    }

    @Override
    public MutableLocalComponentMetaData convert(Object source) {
        DefaultJavaSourceSetResolveContext context = (DefaultJavaSourceSetResolveContext) source;
        ModuleVersionIdentifier id = new DefaultModuleVersionIdentifier(
            context.getProject().getGroup().toString(), context.getProject().getName(), context.getProject().getVersion().toString()
        );
        ComponentIdentifier component = new DefaultLibraryComponentIdentifier(context.getProject().getPath(), context.getSourceSet().getName());
        return new DefaultLocalComponentMetaData(id, component, Project.DEFAULT_STATUS);
    }

}
