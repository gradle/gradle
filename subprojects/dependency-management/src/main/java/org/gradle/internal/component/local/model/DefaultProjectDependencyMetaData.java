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

package org.gradle.internal.component.local.model;

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.component.ProjectComponentSelector;

public class DefaultProjectDependencyMetaData extends DefaultDslOriginDependencyMetaData implements ProjectDependencyMetaData {
    public DefaultProjectDependencyMetaData(DependencyDescriptor dependencyDescriptor, ProjectDependency source) {
        super(dependencyDescriptor, source);
    }

    @Override
    public ProjectComponentSelector getSelector() {
        return new DefaultProjectComponentSelector(((ProjectDependency)getSource()).getDependencyProject().getPath());
    }
}
