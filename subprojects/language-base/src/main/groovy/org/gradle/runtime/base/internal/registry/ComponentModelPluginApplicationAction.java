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

package org.gradle.runtime.base.internal.registry;

import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Project;
import org.gradle.api.internal.plugins.PluginApplication;
import org.gradle.api.internal.plugins.PluginApplicationAction;
import org.gradle.api.plugins.PluginAware;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.runtime.base.*;
import org.gradle.runtime.base.internal.DefaultComponentSpecIdentifier;
import org.gradle.runtime.base.library.DefaultLibrarySpec;

import java.lang.annotation.IncompleteAnnotationException;

public class ComponentModelPluginApplicationAction implements PluginApplicationAction {

    private Instantiator instantiator;

    public ComponentModelPluginApplicationAction(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    public void execute(PluginApplication pluginApplication) {
        Class<?>[] declaredClasses = pluginApplication.getPlugin().getClass().getDeclaredClasses();
        if (declaredClasses.length != 0) {
            for (Class<?> declaredClass : declaredClasses) {
                if (declaredClass.isAnnotationPresent(ComponentModel.class)) {
                    ComponentModel componentModel = declaredClass.getAnnotation(ComponentModel.class);
                    validate(componentModel);
                    registerComponentModel(pluginApplication.getTarget(), componentModel);
                }
            }
        }
    }

    private void validate(ComponentModel componentModel) {
        try {
            Class<? extends LibrarySpec> type = componentModel.type();
            if (!LibrarySpec.class.isAssignableFrom(type)) {
                throw new InvalidComponentModelException(String.format("ComponentModel type '%s' must extend '%s'.", type.getSimpleName(), LibrarySpec.class.getSimpleName()));
            }
        } catch (IncompleteAnnotationException ex) {
            throw new InvalidComponentModelException("Parameter 'type' not declared in ComponentModel declaration.", ex);
        }
        try {
            Class<? extends LibrarySpec> implementation = componentModel.implementation();
            if(!componentModel.type().isAssignableFrom(implementation)){
                throw new InvalidComponentModelException(String.format("ComponentModel implementation '%s' must implement '%s'.", implementation.getSimpleName(), componentModel.type().getSimpleName()));
            }
            if (!DefaultLibrarySpec.class.isAssignableFrom(implementation)) {
                throw new InvalidComponentModelException(String.format("ComponentModel implementation '%s' must extend '%s'.", implementation.getSimpleName(), DefaultLibrarySpec.class.getSimpleName()));
            }
            try{
                implementation.getConstructor();
            }catch(NoSuchMethodException nsmException){

                throw new InvalidComponentModelException(String.format("ComponentModel implementation '%s' must have public default constructor.", implementation.getSimpleName()));
            }
        } catch (IncompleteAnnotationException ex) {
            throw new InvalidComponentModelException("Parameter 'implementation' not declared in ComponentModel declaration.", ex);
        }
    }

    private void registerComponentModel(PluginAware target, final ComponentModel componentModel) {
        if (!(target instanceof Project))  {
            throw new InvalidComponentModelException("ComponentModel can only be declared for project plugins.");
        }

        target.getPlugins().apply(ComponentModelBasePlugin.class);

        final Project project = (Project) target;
        final ProjectSourceSet projectSourceSet = project.getExtensions().getByType(ProjectSourceSet.class);
        ComponentSpecContainer componentSpecs = project.getExtensions().getByType(ComponentSpecContainer.class);
        try{
            componentSpecs.registerFactory(componentModel.type(), new NamedDomainObjectFactory() {
                public Object create(String name) {
                    FunctionalSourceSet componentSourceSet = projectSourceSet.maybeCreate(name);
                    ComponentSpecIdentifier id = new DefaultComponentSpecIdentifier(project.getPath(), name);
                    return DefaultLibrarySpec.create((Class<DefaultLibrarySpec>) componentModel.implementation(), id, componentSourceSet, instantiator);
                }
            });
        }catch(GradleException ex){
            throw new InvalidComponentModelException(String.format("Cannot declare component of type '%s'.", componentModel.type().getSimpleName()), ex);
        }
    }
}
