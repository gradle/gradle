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
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.publication.maven.internal.VersionRangeMapper;
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal;
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.util.GUtil;

import java.io.File;
import java.io.IOException;
import java.io.Writer;

public class MavenPomFileGenerator {

    private static final String POM_FILE_ENCODING = "UTF-8";
    private static final String POM_VERSION = "4.0.0";

    private Model model = new Model();
    private XmlTransformer xmlTransformer = new XmlTransformer();
    private final VersionRangeMapper versionRangeMapper;

    public MavenPomFileGenerator(MavenProjectIdentity identity, VersionRangeMapper versionRangeMapper) {
        this.versionRangeMapper = versionRangeMapper;
        model.setModelVersion(POM_VERSION);
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
        return model;
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
        mavenDependency.setVersion(mapToMavenSyntax(dependency.getVersion()));
        mavenDependency.setType(type);
        mavenDependency.setScope(scope);
        mavenDependency.setClassifier(classifier);

        for (ExcludeRule excludeRule : dependency.getExcludeRules()) {
            Exclusion exclusion = new Exclusion();
            exclusion.setGroupId(GUtil.elvis(excludeRule.getGroup(), "*"));
            exclusion.setArtifactId(GUtil.elvis(excludeRule.getModule(), "*"));
            mavenDependency.addExclusion(exclusion);
        }

        getModel().addDependency(mavenDependency);
    }

    private String mapToMavenSyntax(String version) {
        return versionRangeMapper.map(version);
    }

    public MavenPomFileGenerator withXml(final Action<XmlProvider> action) {
        xmlTransformer.addAction(action);
        return this;
    }

    public MavenPomFileGenerator writeTo(File file) {
        xmlTransformer.transform(file, POM_FILE_ENCODING, new Action<Writer>() {
            public void execute(Writer writer) {
                try {
                    new MavenXpp3Writer().write(writer, model);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
        return this;
    }

}
