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

package org.gradle.api.publish.maven.internal.tasks;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.internal.xml.XmlTransformer;
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal;
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;

import java.io.File;
import java.io.IOException;
import java.io.Writer;

public class MavenPomFileGenerator {

    private static final String POM_FILE_ENCODING = "UTF-8";
    private static final String POM_VERSION = "4.0.0";

    private MavenProject mavenProject = new MavenProject();
    private XmlTransformer xmlTransformer = new XmlTransformer();

    public MavenPomFileGenerator(MavenProjectIdentity identity) {
        mavenProject.setModelVersion(POM_VERSION);
        Model model = getModel();
        model.setGroupId(identity.getGroupId());
        model.setArtifactId(identity.getArtifactId());
        model.setVersion(identity.getVersion());
    }

    public MavenPomFileGenerator setPackaging(String packaging) {
        getModel().setPackaging(packaging);
        return this;
    }

    private Model getModel() {
        return mavenProject.getModel();
    }

    public void addRuntimeDependency(MavenDependencyInternal dependency) {
        addDependency(dependency, "runtime");
    }

    private void addDependency(MavenDependencyInternal mavenDependency, String scope) {
        if (mavenDependency.getArtifacts().size() == 0) {
            addDependency(mavenDependency, mavenDependency.getArtifactId(), scope, null, null);
        } else {
            for (DependencyArtifact artifact : mavenDependency.getArtifacts()) {
                addDependency(mavenDependency, artifact.getName(), scope, artifact.getType(), artifact.getClassifier());
            }
        }
    }

    private void addDependency(MavenDependencyInternal dependency, String artifactId, String scope, String type, String classifier) {
        Dependency mavenDependency = new Dependency();
        mavenDependency.setGroupId(dependency.getGroupId());
        mavenDependency.setArtifactId(artifactId);
        mavenDependency.setVersion(dependency.getVersion());
        mavenDependency.setType(type);
        mavenDependency.setScope(scope);
        mavenDependency.setClassifier(classifier);

        getModel().addDependency(mavenDependency);
    }

    public MavenPomFileGenerator withXml(final Action<XmlProvider> action) {
        xmlTransformer.addAction(action);
        return this;
    }

    public MavenPomFileGenerator writeTo(File file) {
        xmlTransformer.transform(file, POM_FILE_ENCODING, new Action<Writer>() {
            public void execute(Writer writer) {
                try {
                    mavenProject.writeModel(writer);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
        return this;
    }

}
