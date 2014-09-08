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
package org.gradle.api.publication.maven.internal;

import groovy.lang.Closure;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.listener.ActionBroadcast;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.List;

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

    @SuppressWarnings("unchecked")
    public DefaultMavenPom setDependencies(List<?> dependencies) {
        getModel().setDependencies((List<Dependency>) dependencies);
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

    public DefaultMavenPom setModel(Object model) {
        this.mavenProject = new MavenProject((Model) model);
        return this;
    }

    public MavenProject getMavenProject() {
        return mavenProject;
    }

    public DefaultMavenPom setMavenProject(MavenProject mavenProject) {
        this.mavenProject = mavenProject;
        return this;
    }

    @SuppressWarnings("unchecked")
    public List<Dependency> getGeneratedDependencies() {
        if (configurations == null) {
            return Collections.emptyList();
        }
        return (List<Dependency>) pomDependenciesConverter.convert(getScopeMappings(), configurations);
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
        try {
            getEffectivePom().writeNonEffectivePom(pomWriter);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    public DefaultMavenPom writeTo(Object path) {
        IoActions.writeTextFile(fileResolver.resolve(path), POM_FILE_ENCODING, new Action<BufferedWriter>() {
            public void execute(BufferedWriter writer) {
                writeTo(writer);
            }
        });
        return this;
    }

    private void writeNonEffectivePom(final Writer pomWriter) throws IOException {
        try {
            withXmlActions.transform(pomWriter, POM_FILE_ENCODING, new ErroringAction<Writer>() {
                protected void doExecute(Writer writer) throws IOException {
                    mavenProject.writeModel(writer);
                }
            });
        } finally {
            pomWriter.close();
        }
    }

    public DefaultMavenPom whenConfigured(final Closure closure) {
        whenConfiguredActions.add(new ClosureBackedAction<MavenPom>(closure));
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
