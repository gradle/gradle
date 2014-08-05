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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.MDArtifact;

import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Sets.newLinkedHashSet;
import static java.util.Arrays.asList;
import static org.gradle.api.internal.artifacts.ivyservice.IvyUtil.artifactsEqual;

public class IvyMDArtifact {

    private Set<String> configurations = new LinkedHashSet<String>();
    private final DefaultModuleDescriptor md;
    private final String artName;
    private final String type;
    private final String ext;
    private final URL url;
    private final Map<String, String> extraAttributes;

    public IvyMDArtifact(DefaultModuleDescriptor md, String artName, String type, String ext, URL url, Map<String, String> extraAttributes) {
        this.md = md;
        this.artName = artName;
        this.type = type;
        this.ext = ext;
        this.url = url;
        this.extraAttributes = extraAttributes;
    }

    public IvyMDArtifact(DefaultModuleDescriptor md, String artName, String type, String ext) {
        this(md, artName, type, ext, null, null);
    }

    public IvyMDArtifact addConfiguration(String confName) {
        configurations.add(confName);
        return this;
    }

    public Set<String> getConfigurations() {
        return configurations;
    }

    public void addTo(DefaultModuleDescriptor target) {
        if (configurations.isEmpty()) {
            throw new IllegalArgumentException("Artifact should be attached to at least one configuration.");
        }

        MDArtifact newArtifact = new MDArtifact(md, artName, type, ext, url, extraAttributes);
        //Adding the artifact will replace any existing artifact
        //This potentially leads to loss of information - the configurations of the replaced artifact are lost (see GRADLE-123)
        //Hence we attempt to find an existing artifact and merge the information
        Artifact[] allArtifacts = target.getAllArtifacts();
        for (Artifact existing : allArtifacts) {
            if (artifactsEqual(existing, newArtifact)) {
                if (!(existing instanceof MDArtifact)) {
                    throw new IllegalArgumentException("Cannot update an existing artifact (" + existing + ") in provided module descriptor (" + target + ")"
                            + " because the artifact is not an instance of MDArtifact." + target);
                }
                addArtifact((MDArtifact) existing, this.configurations, target);
                return; //there is only one matching artifact
            }
        }
        addArtifact(newArtifact, this.configurations, target);
    }

    private static void addArtifact(MDArtifact artifact, Set<String> configurations, DefaultModuleDescriptor target) {
        //The existing artifact configurations will be first
        Set<String> existingConfigurations = newLinkedHashSet(asList(artifact.getConfigurations()));
        for (String c : configurations) {
            if (!existingConfigurations.contains(c)) {
                artifact.addConfiguration(c);
                target.addArtifact(c, artifact);
            }
        }
    }
}
