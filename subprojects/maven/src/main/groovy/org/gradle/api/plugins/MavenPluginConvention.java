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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.maven.Conf2ScopeMapping;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.publication.maven.internal.MavenFactory;
import org.gradle.api.publication.maven.internal.MavenPomMetaInfoProvider;
import org.gradle.internal.Factory;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.util.Collections;

/**
 * Properties and methods added by the {@link org.gradle.api.plugins.MavenPlugin}.
 */
public class MavenPluginConvention implements MavenPomMetaInfoProvider {
    private final ProjectInternal project;
    private final MavenFactory mavenFactory;
    private Conf2ScopeMappingContainer conf2ScopeMappings;
    private Object pomDir;

    public MavenPluginConvention(ProjectInternal project, MavenFactory mavenFactory) {
        this.project = project;
        this.mavenFactory = mavenFactory;
        conf2ScopeMappings = mavenFactory.createConf2ScopeMappingContainer(Collections.<Configuration, Conf2ScopeMapping>emptyMap());
    }


    /**
     * Returns the set of rules for how to map Gradle dependencies to Maven scopes.
     *
     * @return The mapping rules.
     */
    public Conf2ScopeMappingContainer getConf2ScopeMappings() {
        return conf2ScopeMappings;
    }

    public void setConf2ScopeMappings(Conf2ScopeMappingContainer conf2ScopeMappings) {
        this.conf2ScopeMappings = conf2ScopeMappings;
    }

    /**
     * Returns the directory to generate Maven POMs into.
     */
    public File getMavenPomDir() {
        if (pomDir == null) {
            return new File(project.getBuildDir(), "poms");
        }
        return project.getFileResolver().resolve(pomDir);
    }

    /**
     * Sets the directory to generate Maven POMs into.
     *
     * @param pomDir The new POM directory. Evaluated as per {@link org.gradle.api.Project#file(Object)}.
     */
    public void setMavenPomDir(Object pomDir) {
        this.pomDir = pomDir;
    }

    /**
     * Creates a new {@link MavenPom}.
     *
     * @return The POM instance.
     */
    public MavenPom pom() {
        return pom(null);
    }

    /**
     * Creates and configures a new {@link MavenPom}. The given closure is executed to configure the new POM instance.
     *
     * @param configureClosure The closure to use to configure the POM instance.
     * @return The POM instance.
     */
    public MavenPom pom(Closure configureClosure) {
        Factory<MavenPom> pomFactory = mavenFactory.createMavenPomFactory(project.getConfigurations(),
                conf2ScopeMappings.getMappings(),
                project.getFileResolver());
        MavenPom pom = pomFactory.create();
        pom.setGroupId(project.getGroup().toString());
        pom.setArtifactId(project.getName());
        pom.setVersion(project.getVersion().toString());
        return ConfigureUtil.configure(configureClosure, pom);
    }
}
