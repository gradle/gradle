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
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.artifacts.maven.XmlProvider;
import org.gradle.api.internal.XmlTransformer;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.PomDependenciesConverter;
import org.gradle.api.internal.artifacts.publish.maven.pombuilder.CustomModelBuilder;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.listener.ActionBroadcast;

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
    private ActionBroadcast<MavenPom> whenConfiguredActions = new ActionBroadcast<MavenPom>();
    private XmlTransformer withXmlActions = new XmlTransformer();
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

    public ConfigurationContainer getConfigurations() {
        return configurations;
    }

    public DefaultMavenPom setConfigurations(ConfigurationContainer configurations) {
        this.configurations = configurations;
        return this;
    }
    
    public DefaultMavenPom setGroupId(String groupId) {
        getModel().setGroupId(groupId);
        return this;
    }

    public String getGroupId() {
        return getModel().getGroupId();
    }

    public DefaultMavenPom setArtifactId(String artifactId) {
        getModel().setArtifactId(artifactId);
        return this;
    }

    public String getArtifactId() {
        return getModel().getArtifactId();
    }

    public DefaultMavenPom setDependencies(List<Dependency> dependencies) {
        getModel().setDependencies(dependencies);
        return this;
    }

    public List<Dependency> getDependencies() {
        return getModel().getDependencies();
    }

    public DefaultMavenPom setName(String name) {
        getModel().setName(name);
        return this;
    }

    public String getName() {
        return getModel().getName();
    }

    public DefaultMavenPom setVersion(String version) {
        getModel().setVersion(version);
        return this;
    }

    public String getVersion() {
        return getModel().getVersion();
    }

    public String getPackaging() {
        return getModel().getPackaging();
    }

    public DefaultMavenPom setPackaging(String packaging) {
        getModel().setPackaging(packaging);
        return this;
    }

    public DefaultMavenPom project(Closure cl) {
        CustomModelBuilder pomBuilder = new CustomModelBuilder(getModel());
        InvokerHelper.invokeMethod(pomBuilder, "project", cl);
        return this;
    }

    public Model getModel() {
        return mavenProject.getModel();
    }

    public DefaultMavenPom setModel(Model model) {
        this.mavenProject = new MavenProject(model);
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
        whenConfiguredActions.execute(effectivePom);
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
            FileWriter writer = new FileWriter(file);
            try {
                return writeTo(writer);
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeNonEffectivePom(final Writer pomWriter) {
        try {
            final StringWriter stringWriter = new StringWriter();
            mavenProject.writeModel(stringWriter);
            withXmlActions.transform(stringWriter.toString(), pomWriter);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            IOUtils.closeQuietly(pomWriter);
        }
    }

    public DefaultMavenPom whenConfigured(final Closure closure) {
        whenConfiguredActions.add(closure);
        return this;
    }

    public DefaultMavenPom whenConfigured(final Action<MavenPom> action) {
        whenConfiguredActions.add(action);
        return this;
    }

    public DefaultMavenPom withXml(final Closure closure) {
        withXmlActions.addAction(closure);
        return this;
    }

    public DefaultMavenPom withXml(final Action<XmlProvider> action) {
        withXmlActions.addAction(action);
        return this;
    }
}
