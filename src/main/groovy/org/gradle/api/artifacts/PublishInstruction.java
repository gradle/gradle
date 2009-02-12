/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.artifacts;

import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class PublishInstruction {
    private ModuleDescriptorInstruction moduleDescriptor = new ModuleDescriptorInstruction();

    private Spec<PublishArtifact> artifactSpec = Specs.satisfyAll();

    public ModuleDescriptorInstruction getModuleDescriptor() {
        return moduleDescriptor;
    }

    public Spec<PublishArtifact> getArtifactSpec() {
        return artifactSpec;
    }

    public void setArtifactSpec(Spec<PublishArtifact> artifactSpec) {
        this.artifactSpec = artifactSpec;
    }

    public static class ModuleDescriptorInstruction {
        private boolean publish = true;

        private Spec<Configuration> configurationSpec = Specs.satisfyAll();

        private Spec<Dependency> dependencySpec = Specs.satisfyAll();

        private File ivyFileParentDir = null;

        public boolean isPublish() {
            return publish;
        }

        public void setPublish(boolean publish) {
            this.publish = publish;
        }

        public Spec<Configuration> getConfigurationSpec() {
            return configurationSpec;
        }

        public void setConfigurationSpec(Spec<Configuration> configurationSpec) {
            this.configurationSpec = configurationSpec;
        }

        public Spec<Dependency> getDependencySpec() {
            return dependencySpec;
        }

        public void setDependencySpec(Spec<Dependency> dependencySpec) {
            this.dependencySpec = dependencySpec;
        }

        public File getIvyFileParentDir() {
            return ivyFileParentDir;
        }

        public void setIvyFileParentDir(File ivyFileParentDir) {
            this.ivyFileParentDir = ivyFileParentDir;
        }
    }
}
