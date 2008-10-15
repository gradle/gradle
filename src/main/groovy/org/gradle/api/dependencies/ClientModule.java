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

import groovy.lang.Closure;
import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.internal.dependencies.DefaultDependencyContainer;
import org.gradle.api.internal.dependencies.DependencyDescriptorFactory;
import org.gradle.api.internal.dependencies.DependencyFactory;
import org.gradle.util.WrapUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class ClientModule extends DefaultDependencyContainer implements Dependency {
    public static final String CLIENT_MODULE_KEY = "org.gradle.clientModule";

    private String id;

    private Set confs;

    private DependencyDescriptorFactory dependencyDescriptorFactory = new DependencyDescriptorFactory();

    public ClientModule() {}

    public ClientModule(DependencyFactory dependencyFactory, Set confs,
                 String id, Map moduleRegistry) {
        super(dependencyFactory, WrapUtil.toList(Dependency.DEFAULT_CONFIGURATION));
        this.id = id;
        setClientModuleRegistry(moduleRegistry);
        this.confs = confs;
    }

    public DependencyDescriptor createDepencencyDescriptor(ModuleDescriptor parent) {
        DependencyDescriptor dd = dependencyDescriptorFactory.createDescriptor(parent, id, false, true, true, confs, new ArrayList<ExcludeRule>(),
                WrapUtil.toMap(CLIENT_MODULE_KEY, id));
        addModuleDescriptors(dd.getDependencyRevisionId());
        return dd;
    }

    private void addModuleDescriptors(ModuleRevisionId moduleRevisionId) {
        DefaultModuleDescriptor moduleDescriptor = new DefaultModuleDescriptor(moduleRevisionId,
                "release", null);
        moduleDescriptor.addConfiguration(new Configuration(Dependency.DEFAULT_CONFIGURATION));
        addDependencyDescriptors(moduleDescriptor);
        moduleDescriptor.addArtifact(Dependency.DEFAULT_CONFIGURATION, new DefaultArtifact(moduleRevisionId, null, moduleRevisionId.getName(), "jar", "jar"));
        this.getClientModuleRegistry().put(id, moduleDescriptor);
    }

    public void addDependencyDescriptors(DefaultModuleDescriptor moduleDescriptor) {
        List<DependencyDescriptor> dependencyDescriptors = new ArrayList<DependencyDescriptor>();
        for (Dependency dependency : getDependencies()) {
            dependencyDescriptors.add(dependency.createDepencencyDescriptor(moduleDescriptor));
        }
        dependencyDescriptors.addAll(getDependencyDescriptors());
        for (DependencyDescriptor dependencyDescriptor : dependencyDescriptors) {
            moduleDescriptor.addDependency(dependencyDescriptor);
        }
    }

    public void initialize() {
        // do nothing
    }

    public void dependencies(List<String> confs, Object... dependencies) {
        if (!confs.equals(getDefaultConfs())) {
            throw new UnsupportedOperationException("You can assign confs " + confs + " to dependencies in a client module.");
        }
        super.dependencies(confs, dependencies);
    }

    public Dependency dependency(List confs, Object id) {
        return dependency(confs, id, null);
    }

    public Dependency dependency(List confs, Object id, Closure configureClosure) {
        if (!confs.equals(getDefaultConfs())) {
            throw new UnsupportedOperationException("You can assign confs $confs to dependencies in a client module.");
        }
        return super.dependency(confs, id, configureClosure);
    }

    public ClientModule clientModule(List confs, String artifact) {
        return clientModule(confs, artifact, null);
    }

    public ClientModule clientModule(List confs, String artifact, Closure configureClosure) {
        if (!confs.equals(getDefaultConfs())) {
            throw new UnsupportedOperationException("You can assign confs $confs to dependencies in a client module.");
        }
        return super.clientModule(confs, artifact, configureClosure);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Set getConfs() {
        return confs;
    }

    public void setConfs(Set confs) {
        this.confs = confs;
    }

    public DependencyDescriptorFactory getDependencyDescriptorFactory() {
        return dependencyDescriptorFactory;
    }

    public void setDependencyDescriptorFactory(DependencyDescriptorFactory dependencyDescriptorFactory) {
        this.dependencyDescriptorFactory = dependencyDescriptorFactory;
    }
}
