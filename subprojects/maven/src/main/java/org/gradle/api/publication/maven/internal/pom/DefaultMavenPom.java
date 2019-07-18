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
package org.gradle.api.publication.maven.internal.pom;

import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.project.MavenProject;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.internal.MutableActionSet;
import org.gradle.util.ConfigureUtil;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.List;

public class DefaultMavenPom implements MavenPom {

    private PomDependenciesConverter pomDependenciesConverter;
    private PathToFileResolver fileResolver;
    private Model model = new MavenProject().getModel();
    private Conf2ScopeMappingContainer scopeMappings;
    private MutableActionSet<MavenPom> whenConfiguredActions = new MutableActionSet<MavenPom>();
    private XmlTransformer withXmlActions = new XmlTransformer();
    private ConfigurationContainer configurations;

    public DefaultMavenPom(ConfigurationContainer configurationContainer, Conf2ScopeMappingContainer scopeMappings, PomDependenciesConverter pomDependenciesConverter,
                           PathToFileResolver fileResolver) {
        this.configurations = configurationContainer;
        this.scopeMappings = scopeMappings;
        this.pomDependenciesConverter = pomDependenciesConverter;
        this.fileResolver = fileResolver;
        model.setModelVersion("4.0.0");
    }

    @Override
    public Conf2ScopeMappingContainer getScopeMappings() {
        return scopeMappings;
    }

    @Override
    public ConfigurationContainer getConfigurations() {
        return configurations;
    }

    @Override
    public DefaultMavenPom setConfigurations(ConfigurationContainer configurations) {
        this.configurations = configurations;
        return this;
    }

    @Override
    public DefaultMavenPom setGroupId(String groupId) {
        getModel().setGroupId(groupId);
        return this;
    }

    @Override
    public String getGroupId() {
        return getModel().getGroupId();
    }

    @Override
    public DefaultMavenPom setArtifactId(String artifactId) {
        getModel().setArtifactId(artifactId);
        return this;
    }

    @Override
    public String getArtifactId() {
        return getModel().getArtifactId();
    }

    @Override
    @SuppressWarnings("unchecked")
    public DefaultMavenPom setDependencies(List<?> dependencies) {
        getModel().setDependencies((List<Dependency>) dependencies);
        return this;
    }

    @Override
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

    @Override
    public DefaultMavenPom setVersion(String version) {
        getModel().setVersion(version);
        return this;
    }

    @Override
    public String getVersion() {
        return getModel().getVersion();
    }

    @Override
    public String getPackaging() {
        return getModel().getPackaging();
    }

    @Override
    public DefaultMavenPom setPackaging(String packaging) {
        getModel().setPackaging(packaging);
        return this;
    }

    @Override
    public DefaultMavenPom project(Closure cl) {
        CustomModelBuilder pomBuilder = new CustomModelBuilder(getModel());
        InvokerHelper.invokeMethod(pomBuilder, "project", cl);
        return this;
    }

    @Override
    public DefaultMavenPom project(final Action<? super GroovyObject> action) {
        return project(new Closure(this, this) {
            @SuppressWarnings("unused")
            public void doCall() {
                action.execute((GroovyObject) getDelegate());
            }
        });
    }

    @Override
    public Model getModel() {
        return model;
    }

    @Override
    public DefaultMavenPom setModel(Object model) {
        this.model = (Model) model;
        return this;
    }

    public MavenProject getMavenProject() {
        return new MavenProject(model);
    }

    public DefaultMavenPom setMavenProject(MavenProject mavenProject) {
        this.model = mavenProject.getModel();
        return this;
    }

    @SuppressWarnings("unchecked")
    public List<Dependency> getGeneratedDependencies() {
        if (configurations == null) {
            return Collections.emptyList();
        }
        return (List<Dependency>) pomDependenciesConverter.convert(getScopeMappings(), configurations);
    }

    @Override
    public DefaultMavenPom getEffectivePom() {
        DefaultMavenPom effectivePom = new DefaultMavenPom(null, this.scopeMappings, pomDependenciesConverter, fileResolver);
        effectivePom.setModel(model.clone());
        effectivePom.getDependencies().addAll(getGeneratedDependencies());
        effectivePom.withXmlActions = withXmlActions;
        whenConfiguredActions.execute(effectivePom);
        return effectivePom;
    }

    public PomDependenciesConverter getPomDependenciesConverter() {
        return pomDependenciesConverter;
    }

    public DefaultMavenPom setFileResolver(PathToFileResolver fileResolver) {
        this.fileResolver = fileResolver;
        return this;
    }

    @Override
    public DefaultMavenPom writeTo(final Writer pomWriter) {
        try {
            getEffectivePom().writeNonEffectivePom(pomWriter);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    @Override
    public DefaultMavenPom writeTo(Object path) {
        IoActions.writeTextFile(fileResolver.resolve(path), POM_FILE_ENCODING, new Action<BufferedWriter>() {
            @Override
            public void execute(BufferedWriter writer) {
                writeTo(writer);
            }
        });
        return this;
    }

    private void writeNonEffectivePom(final Writer pomWriter) throws IOException {
        try {
            withXmlActions.transform(pomWriter, POM_FILE_ENCODING, new ErroringAction<Writer>() {
                @Override
                protected void doExecute(Writer writer) throws IOException {
                    new MavenXpp3Writer().write(writer, getModel());
                }
            });
        } finally {
            pomWriter.close();
        }
    }

    @Override
    public DefaultMavenPom whenConfigured(final Closure closure) {
        whenConfiguredActions.add(ConfigureUtil.configureUsing(closure));
        return this;
    }

    @Override
    public DefaultMavenPom whenConfigured(final Action<MavenPom> action) {
        whenConfiguredActions.add(action);
        return this;
    }

    @Override
    public DefaultMavenPom withXml(final Closure closure) {
        withXmlActions.addAction(closure);
        return this;
    }

    @Override
    public DefaultMavenPom withXml(final Action<XmlProvider> action) {
        withXmlActions.addAction(action);
        return this;
    }
}
