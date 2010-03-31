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

import groovy.lang.Closure;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.internal.artifacts.publish.maven.DefaultMavenPom;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultConf2ScopeMappingContainer;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultExcludeRuleConverter;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultPomDependenciesConverter;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.ConfigureUtil;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class MavenPluginConvention {
    private ProjectInternal project;
    private String pomDirName = "poms";
    private Conf2ScopeMappingContainer conf2ScopeMappings = new DefaultConf2ScopeMappingContainer();

    public MavenPluginConvention(ProjectInternal project) {
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
        return project.getFileResolver().withBaseDir(project.getBuildDir()).resolve(pomDirName);
    }

    public MavenPom pom() {
        return pom(null);
    }

    public MavenPom pom(Closure configureClosure) {
        DefaultMavenPom pom = new DefaultMavenPom(project.getConfigurations(),
                new DefaultConf2ScopeMappingContainer(conf2ScopeMappings.getMappings()),
                new DefaultPomDependenciesConverter(new DefaultExcludeRuleConverter()),
                ((ProjectInternal) project).getFileResolver());
        pom.setGroupId(project.getGroup().toString());
        pom.setArtifactId(project.getName());
        pom.setVersion(project.getVersion().toString());
        return ConfigureUtil.configure(configureClosure, pom);
    }
}
