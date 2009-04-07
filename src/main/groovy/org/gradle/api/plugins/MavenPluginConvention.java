/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.plugins;

import org.gradle.api.Project;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultConf2ScopeMappingContainer;

import java.io.File;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class MavenPluginConvention {
    private Project project;
    private String pomDirName = "poms";
    private Conf2ScopeMappingContainer conf2ScopeMappings = new DefaultConf2ScopeMappingContainer();
    
    public MavenPluginConvention(Project project, Map customValues) {
        this.project = project;
    }

    public String getPomDirName() {
        return pomDirName;
    }

    public void setPomDirName(String pomDirName) {
        this.pomDirName = pomDirName;
    }

    public Conf2ScopeMappingContainer getConf2ScopeMappings() {
        return conf2ScopeMappings;
    }

    public void setConf2ScopeMappings(Conf2ScopeMappingContainer conf2ScopeMappings) {
        this.conf2ScopeMappings = conf2ScopeMappings;
    }

    public File getPomDir() {
        return new File(project.getBuildDir(), pomDirName);
    }
}
