/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.maven.internal;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.ErroringAction;
import org.gradle.api.internal.IoActions;
import org.gradle.api.internal.xml.XmlTransformer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;

public class MavenPomGenerator {

    String POM_FILE_ENCODING = "UTF-8";

    private MavenProject mavenProject = new MavenProject();
    private XmlTransformer withXmlActions = new XmlTransformer();

    public MavenPomGenerator() {
        mavenProject.setModelVersion("4.0.0");
    }

    public MavenPomGenerator setGroupId(String groupId) {
        getModel().setGroupId(groupId);
        return this;
    }

    public MavenPomGenerator setArtifactId(String artifactId) {
        getModel().setArtifactId(artifactId);
        return this;
    }

    public MavenPomGenerator setName(String name) {
        getModel().setName(name);
        return this;
    }

    public MavenPomGenerator setVersion(String version) {
        getModel().setVersion(version);
        return this;
    }

    public MavenPomGenerator setPackaging(String packaging) {
        getModel().setPackaging(packaging);
        return this;
    }

    public MavenPomGenerator withXml(final Action<XmlProvider> action) {
        withXmlActions.addAction(action);
        return this;
    }

    private Model getModel() {
        return mavenProject.getModel();
    }

    public MavenPomGenerator writeTo(File file) {
        IoActions.writeFile(file, POM_FILE_ENCODING, new Action<BufferedWriter>() {
            public void execute(BufferedWriter writer) {
                try {
                    writeWithXmlActions(writer);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
        return this;
    }

    private void writeWithXmlActions(final Writer pomWriter) throws IOException {
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

    public void addRuntimeDependency(org.gradle.api.artifacts.Dependency dependency) {
        if (dependency instanceof ModuleDependency) {
            addDependency((ModuleDependency) dependency, "runtime");
        }
    }

    private void addDependency(ModuleDependency moduleDependency, String scope) {
        if (moduleDependency.getArtifacts().size() == 0) {
            getModel().addDependency(createMavenDependency(moduleDependency, moduleDependency.getName(), null, scope, null));
        } else {
            for (DependencyArtifact artifact : moduleDependency.getArtifacts()) {
                getModel().addDependency(createMavenDependency(moduleDependency, artifact.getName(), artifact.getType(), scope, artifact.getClassifier()));
            }
        }
    }

    private Dependency createMavenDependency(ModuleDependency dependency, String name, String type, String scope, String classifier) {
        Dependency mavenDependency =  new Dependency();
        mavenDependency.setGroupId(dependency.getGroup());
        if (dependency instanceof ProjectDependency) {
            mavenDependency.setArtifactId(determineProjectDependencyArtifactId((ProjectDependency) dependency));
        } else {
            mavenDependency.setArtifactId(name);
        }
        mavenDependency.setVersion(dependency.getVersion());
        mavenDependency.setType(type);
        mavenDependency.setScope(scope);
        mavenDependency.setOptional(false);
        mavenDependency.setClassifier(classifier);
        return mavenDependency;
    }

    private String determineProjectDependencyArtifactId(ProjectDependency dependency) {
        return dependency.getDependencyProject().getName();
    }

}
