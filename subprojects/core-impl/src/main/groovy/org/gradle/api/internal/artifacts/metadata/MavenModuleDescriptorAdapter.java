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

package org.gradle.api.internal.artifacts.metadata;

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;

import java.util.Arrays;
import java.util.Collection;

public class MavenModuleDescriptorAdapter extends ModuleDescriptorAdapter implements MavenModuleVersionMetaData {
    private static final String POM_PACKAGING = "pom";
    private static final Collection<String> JAR_PACKAGINGS = Arrays.asList("ejb", "bundle", "maven-plugin", "eclipse-plugin");
    private String packaging;
    private boolean relocated;

    public static ModuleDescriptorAdapter defaultForDependency(DependencyMetaData dependencyMetaData) {
        DefaultModuleDescriptor moduleDescriptor = createModuleDescriptor(dependencyMetaData);
        return new MavenModuleDescriptorAdapter(moduleDescriptor);
    }

    public MavenModuleDescriptorAdapter(ModuleDescriptor moduleDescriptor) {
        super(moduleDescriptor);
    }

    public MavenModuleDescriptorAdapter(ModuleVersionIdentifier identifier, ModuleDescriptor moduleDescriptor) {
        super(identifier, moduleDescriptor);
    }

    public MavenModuleDescriptorAdapter(ModuleVersionIdentifier moduleVersionIdentifier, ModuleDescriptor moduleDescriptor, ModuleComponentIdentifier componentIdentifier) {
        super(moduleVersionIdentifier, moduleDescriptor, componentIdentifier);
    }

    public String getPackaging() {
        return packaging;
    }

    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    public boolean isRelocated() {
        return relocated;
    }

    public void setRelocated(boolean relocated) {
        this.relocated = relocated;
    }

    public boolean isPomPackaging() {
        return POM_PACKAGING.equals(packaging);
    }

    public boolean isKnownJarPackaging() {
        return packaging == null || "jar".equals(packaging) || JAR_PACKAGINGS.contains(packaging);
    }

    @Override
    public MavenModuleDescriptorAdapter copy() {
        MavenModuleDescriptorAdapter copy = new MavenModuleDescriptorAdapter(getId(), getDescriptor(), getComponentId());
        copyTo(copy);
        copy.packaging = packaging;
        return copy;
    }
}
