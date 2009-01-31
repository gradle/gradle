/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.dependencies;

import org.gradle.api.dependencies.PublishArtifact;
import org.gradle.api.DependencyManager;
import org.gradle.api.filter.FilterSpec;
import org.gradle.api.filter.Filters;
import org.apache.ivy.core.module.descriptor.Artifact;

import java.util.*;
import java.io.File;

/**
 * @author Hans Dockter
 */
public class DefaultArtifactContainer implements ArtifactContainer {
    private Set<PublishArtifact> artifacts = new HashSet<PublishArtifact>();

    private ConfigurationContainer configurationContainer;

    public DefaultArtifactContainer(ConfigurationContainer configurationContainer) {
        this.configurationContainer = configurationContainer;
    }

    public ConfigurationContainer getConfigurationContainer() {
        return configurationContainer;
    }

    public void addArtifacts(PublishArtifact... publishArtifacts) {
        artifacts.addAll(Arrays.asList(publishArtifacts));
    }

    public Set<PublishArtifact> getArtifacts() {
        return artifacts;
    }

    public Set<PublishArtifact> getArtifacts(FilterSpec<PublishArtifact> filter) {
        return new HashSet<PublishArtifact>(Filters.filterIterable(getArtifacts(), filter));
    }

    //    private Map<String, List<PublishArtifact>> artifacts = new HashMap<String, List<PublishArtifact>>();
//
//    private Map<String, List<Artifact>> descriptors = new HashMap<String, List<Artifact>>();
//
//    private List<String> absolutePatterns = new ArrayList<String>();
//
//    private Set<File> parentDirs = new HashSet<File>();
//
//    private String defaultPattern = DependencyManager.DEFAULT_ARTIFACT_PATTERN;
//
//    public void addArtifacts(String configurationName, PublishArtifact... artifacts) {
//        if (this.artifacts.get(configurationName) == null) {
//            this.artifacts.put(configurationName, new ArrayList<PublishArtifact>());
//        }
//        for (PublishArtifact artifact : artifacts) {
//            this.artifacts.get(configurationName).add(artifact);
//        }
//    }
//
//    public Map<String, List<PublishArtifact>> getArtifacts() {
//        return artifacts;
//    }
//
//    public void setArtifacts(Map<String, List<PublishArtifact>> artifacts) {
//        this.artifacts = artifacts;
//    }
//
//    public Map<String, List<Artifact>> getDescriptors() {
//        return descriptors;
//    }
//
//    public void setDescriptors(Map<String, List<Artifact>> descriptors) {
//        this.descriptors = descriptors;
//    }
//
//    public List<String> getAbsolutePatterns() {
//        return absolutePatterns;
//    }
//
//    public void setAbsolutePatterns(List<String> absolutePatterns) {
//        this.absolutePatterns = absolutePatterns;
//    }
//
//    public Set<File> getParentDirs() {
//        return parentDirs;
//    }
//
//    public void setParentDirs(Set<File> parentDirs) {
//        this.parentDirs = parentDirs;
//    }
//
//    public String getDefaultPattern() {
//        return defaultPattern;
//    }
//
//    public void setDefaultPattern(String defaultPattern) {
//        this.defaultPattern = defaultPattern;
//    }
}
