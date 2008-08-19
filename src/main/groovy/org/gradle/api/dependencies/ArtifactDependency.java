/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.dependencies;

import groovy.lang.GString;
import org.apache.ivy.core.module.descriptor.DefaultDependencyArtifactDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.gradle.api.internal.dependencies.DependenciesUtil;
import org.gradle.api.internal.dependencies.DependencyDescriptorFactory;
import org.gradle.util.WrapUtil;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class ArtifactDependency extends AbstractDependency {
    private boolean force = false;

    private DependencyDescriptorFactory dependencyDescriptorFactory = new DependencyDescriptorFactory();

    public ArtifactDependency(Set confs, Object userDependencyDescription) {
        super(confs, userDependencyDescription);
    }

    public boolean isValidDescription(Object userDependencyDescription) {
        if (!DependenciesUtil.hasExtension(userDependencyDescription.toString())) {
            return false;
        }
        int elementCount = (userDependencyDescription.toString()).split(":").length;
        return (elementCount == 3 || elementCount == 4);
    }

    public Class[] userDepencencyDescriptionType() {
        return WrapUtil.toArray(String.class, GString.class);
    }

    public DependencyDescriptor createDepencencyDescriptor() {
        Map<String, String> groups = DependenciesUtil.splitExtension(getUserDependencyDescription().toString());
        DefaultDependencyDescriptor dd = (DefaultDependencyDescriptor) dependencyDescriptorFactory.createDescriptor(
                (String) groups.get("core"), force, false, false, getConfs(), new ArrayList<ExcludeRule>());
        DefaultDependencyArtifactDescriptor artifactDescriptor = new DefaultDependencyArtifactDescriptor(
                dd, dd.getDependencyRevisionId().getName(),
                groups.get("extension"), groups.get("extension"), null, null);
        dd.addDependencyArtifact("*", artifactDescriptor);
        for (String conf : getConfs()) {
            dd.addDependencyConfiguration(conf, Dependency.DEFAULT_CONFIGURATION);
            artifactDescriptor.addConfiguration(conf);
        }
        return dd;
    }

    public DependencyDescriptorFactory getDependencyDescriptorFactory() {
        return dependencyDescriptorFactory;
    }

    public void setDependencyDescriptorFactory(DependencyDescriptorFactory dependencyDescriptorFactory) {
        this.dependencyDescriptorFactory = dependencyDescriptorFactory;
    }

    public boolean isForce() {
        return force;
    }
}
