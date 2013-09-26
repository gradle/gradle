/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativebinaries.internal.configure;

import org.gradle.api.Action;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.nativebinaries.ExecutableContainer;
import org.gradle.nativebinaries.FlavorContainer;
import org.gradle.nativebinaries.LibraryContainer;
import org.gradle.nativebinaries.NativeComponent;
import org.gradle.nativebinaries.internal.DefaultFlavor;

import java.util.Set;

public class CreateDefaultFlavors implements Action<ProjectInternal> {
    public void execute(ProjectInternal project) {
        configureDefaultFlavor(project.getExtensions().getByType(ExecutableContainer.class));
        configureDefaultFlavor(project.getExtensions().getByType(LibraryContainer.class));
    }

    private void configureDefaultFlavor(Set<? extends NativeComponent> components) {
        for (NativeComponent component : components) {
            FlavorContainer flavors = component.getFlavors();
            configureDefaultFlavor(flavors);
        }
    }

    protected void configureDefaultFlavor(FlavorContainer flavors) {
        if (flavors.isEmpty()) {
            flavors.create(DefaultFlavor.DEFAULT);
        }
    }

}
