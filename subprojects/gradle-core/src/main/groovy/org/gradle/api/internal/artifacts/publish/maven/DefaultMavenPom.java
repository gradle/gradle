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
package org.gradle.api.internal.artifacts.publish.maven;

import groovy.lang.Closure;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.artifacts.maven.XmlProvider;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.PomDependenciesConverter;
import org.gradle.api.internal.artifacts.publish.maven.pombuilder.CustomModelBuilder;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.listener.ListenerBroadcast;

import java.io.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Hans Dockter
 */
public class DefaultMavenPom implements MavenPom {
    private PomDependenciesConverter pomDependenciesConverter;
    private FileResolver fileResolver;
    private MavenProject mavenProject = new MavenProject();
    private Conf2ScopeMappingContainer scopeMappings;
    private ListenerBroadcast<Action> whenConfiguredActions = new ListenerBroadcast<Action>(Action.class);
    private ListenerBroadcast<Action> withXmlActions = new ListenerBroadcast<Action>(Action.class);
    private ConfigurationContainer configurations;

    public DefaultMavenPom(ConfigurationContainer configurationContainer, Conf2ScopeMappingContainer scopeMappings, PomDependenciesConverter pomDependenciesConverter,
                           FileResolver fileResolver) {
        this.configurations = configurationContainer;
        this.scopeMappings = scopeMappings;
        this.pomDependenciesConverter = pomDependenciesConverter;
        this.fileResolver = fileResolver;
        mavenProject.setModelVersion("4.0.0");
    }

    public Conf2ScopeMappingContainer getScopeMappings() {
        return scopeMappings;
    }

    public Artifact getArtifact() {
        return mavenProject.getArtifact();
    }

    public DefaultMavenPom setArtifact(Artifact artifact) {
        mavenProject.setArtifact(artifact);
        return this;
    }

    public DefaultMavenPom setGroupId(String groupId) {
        mavenProject.setGroupId(groupId);
        return this;
    }

    public ConfigurationContainer getConfigurations() {
        return configurations;
    }

    public DefaultMavenPom setConfigurations(ConfigurationContainer configurations) {
        this.configurations = configurations;
        return this;
    }

    public String getGroupId() {
        return mavenProject.getGroupId();
    }

    public DefaultMavenPom setArtifactId(String artifactId) {
        mavenProject.setArtifactId(artifactId);
        return this;
    }

    public String getArtifactId() {
        return mavenProject.getArtifactId();
    }

    public DefaultMavenPom setDependencies(List dependencies) {
        mavenProject.setDependencies(dependencies);
        return this;
    }

    public List getDependencies() {
        return mavenProject.getDependencies();
    }

    public DefaultMavenPom setName(String name) {
        mavenProject.setName(name);
        return this;
    }

    public String getName() {
        return mavenProject.getName();
    }

    public DefaultMavenPom setVersion(String version) {
        mavenProject.setVersion(version);
        return this;
    }

    public String getVersion() {
        return mavenProject.getVersion();
    }

    public String getPackaging() {
        return mavenProject.getPackaging();
    }

    public DefaultMavenPom setPackaging(String packaging) {
        mavenProject.setPackaging(packaging);
        return this;
    }

    public DefaultMavenPom project(Closure cl) {
        CustomModelBuilder pomBuilder = new CustomModelBuilder(getMavenProject().getModel());
        InvokerHelper.invokeMethod(pomBuilder, "project", cl);
        return this;
    }

    public MavenProject getMavenProject() {
        return mavenProject;
    }

    public DefaultMavenPom setMavenProject(MavenProject mavenProject) {
        this.mavenProject = mavenProject;
        return this;
    }

    public List<Dependency> getGeneratedDependencies() {
        if (configurations == null) {
            return Collections.emptyList();
        }
        return pomDependenciesConverter.convert(getScopeMappings(), configurations.getAll());
    }

    public DefaultMavenPom getEffectivePom() {
        DefaultMavenPom effectivePom = new DefaultMavenPom(null, this.scopeMappings, pomDependenciesConverter, fileResolver);
        try {
            effectivePom.setMavenProject((MavenProject) mavenProject.clone());
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        effectivePom.getDependencies().addAll(getGeneratedDependencies());
        effectivePom.withXmlActions = withXmlActions;
        whenConfiguredActions.getSource().execute(effectivePom);
        return effectivePom;
    }

    public PomDependenciesConverter getPomDependenciesConverter() {
        return pomDependenciesConverter;
    }

    public FileResolver getFileResolver() {
        return fileResolver;
    }

    public DefaultMavenPom setFileResolver(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
        return this;
    }

    public DefaultMavenPom writeTo(final Writer pomWriter) {
        getEffectivePom().writeNonEffectivePom(pomWriter);
        return this;
    }

    public DefaultMavenPom writeTo(Object path) {
        try {
            File file = fileResolver.resolve(path);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            return writeTo(new FileWriter(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeNonEffectivePom(final Writer pomWriter) {
        try {
            final StringWriter stringWriter = new StringWriter();
            mavenProject.writeModel(stringWriter);
            final StringBuilder stringBuilder = new StringBuilder(stringWriter.toString());
            withXmlActions.getSource().execute(new XmlProvider() {
                public StringBuilder asString() {
                    return stringBuilder;
                }
            });
            IOUtils.write(stringBuilder.toString(), pomWriter);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            IOUtils.closeQuietly(pomWriter);
        }
    }

    public DefaultMavenPom whenConfigured(final Closure closure) {
        whenConfiguredActions.add("execute", closure);
        return this;
    }

    public DefaultMavenPom whenConfigured(final Action<MavenPom> action) {
        whenConfiguredActions.add(action);
        return this;
    }

    public DefaultMavenPom withXml(final Closure closure) {
        withXmlActions.add("execute", closure);
        return this;
    }

    public DefaultMavenPom withXml(final Action<XmlProvider> action) {
        withXmlActions.add(action);
        return this;
    }


}
