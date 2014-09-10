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

package org.gradle.internal.component.external.model;

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.internal.component.model.DependencyMetaData;

import java.util.Arrays;
import java.util.Collection;

public class DefaultMavenModuleResolveMetaData extends AbstractModuleComponentResolveMetaData implements MavenModuleResolveMetaData {
    private static final String POM_PACKAGING = "pom";
    private static final Collection<String> JAR_PACKAGINGS = Arrays.asList("ejb", "bundle", "maven-plugin", "eclipse-plugin");
    private final String packaging;
    private final boolean relocated;

    public DefaultMavenModuleResolveMetaData(ModuleDescriptor moduleDescriptor, String packaging, boolean relocated) {
        super(moduleDescriptor);
        this.packaging = packaging;
        this.relocated = relocated;
    }

    public DefaultMavenModuleResolveMetaData(ModuleVersionIdentifier id, ModuleDescriptor descriptor, ModuleComponentIdentifier componentId, String packaging, boolean relocated) {
        super(id, descriptor, componentId);
        this.packaging = packaging;
        this.relocated = relocated;
    }

    public DefaultMavenModuleResolveMetaData(DependencyMetaData dependencyMetaData) {
        this(IvyUtil.createModuleDescriptor(dependencyMetaData.getDescriptor()), "jar", false);
    }

    @Override
    public DefaultMavenModuleResolveMetaData copy() {
        // TODO:ADAM - need to make a copy of the descriptor (it's effectively immutable at this point so it's not a problem yet)
        DefaultMavenModuleResolveMetaData copy = new DefaultMavenModuleResolveMetaData(getId(), getDescriptor(), getComponentId(), packaging, relocated);
        copyTo(copy);
        return copy;
    }

    public String getPackaging() {
        return packaging;
    }

    public boolean isRelocated() {
        return relocated;
    }

    public boolean isPomPackaging() {
        return POM_PACKAGING.equals(packaging);
    }

    public boolean isKnownJarPackaging() {
        return "jar".equals(packaging) || JAR_PACKAGINGS.contains(packaging);
    }
}
