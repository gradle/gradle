/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.ivy.internal;

import org.gradle.api.artifacts.Module;
import org.gradle.api.internal.XmlTransformer;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;

import java.io.File;

public class IvyNormalizedPublication {

    private final Module module;
    private final File descriptorFile;
    private final XmlTransformer descriptorTransformer;
    private final ConfigurationInternal configuration;

    public IvyNormalizedPublication(Module module, ConfigurationInternal configuration, File descriptorFile, XmlTransformer descriptorTransformer) {
        this.module = module;
        this.configuration = configuration;
        this.descriptorFile = descriptorFile;
        this.descriptorTransformer = descriptorTransformer;
    }

    public Module getModule() {
        return module;
    }

    public ConfigurationInternal getConfiguration() {
        return configuration;
    }

    public File getDescriptorFile() {
        return descriptorFile;
    }

    public XmlTransformer getDescriptorTransformer() {
        return descriptorTransformer;
    }
}
