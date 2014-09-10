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

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.MDArtifact;

import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class BuildableIvyArtifact {

    private final Set<String> configurations = new LinkedHashSet<String>();
    private final String name;
    private final String type;
    private final String ext;
    private final URL url;
    private final Map<String, String> extraAttributes;

    public BuildableIvyArtifact(String name, String type, String ext, URL url, Map<String, String> extraAttributes) {
        this.name = name;
        this.type = type;
        this.ext = ext;
        this.url = url;
        this.extraAttributes = extraAttributes;
    }

    public BuildableIvyArtifact(String name, String type, String ext) {
        this(name, type, ext, null, null);
    }

    public BuildableIvyArtifact addConfiguration(String confName) {
        configurations.add(confName);
        return this;
    }

    public Set<String> getConfigurations() {
        return configurations;
    }

    /**
     * Ivy Artifact instance that is not attached to any configurations yet
     */
    public MDArtifact unattachedArtifact(DefaultModuleDescriptor moduleDescriptor) {
        return new MDArtifact(moduleDescriptor, name, type, ext, url, extraAttributes);
    }
}
