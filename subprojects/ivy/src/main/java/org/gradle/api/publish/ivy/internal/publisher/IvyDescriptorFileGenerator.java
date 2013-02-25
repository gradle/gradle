/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.publisher;

import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.xml.SimpleXmlWriter;
import org.gradle.api.internal.xml.XmlTransformer;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.IvyConfiguration;
import org.gradle.api.publish.ivy.IvyDependency;
import org.gradle.api.publish.ivy.internal.dependency.DefaultIvyDependency;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class IvyDescriptorFileGenerator {
    private static final String IVY_FILE_ENCODING = "UTF-8";
    private static final String IVY_DATE_PATTERN = "yyyyMMddHHmmss";

    private final SimpleDateFormat ivyDateFormat = new SimpleDateFormat(IVY_DATE_PATTERN);
    private final IvyPublicationIdentity projectIdentity;
    private String status;
    private XmlTransformer xmlTransformer = new XmlTransformer();
    private List<IvyConfiguration> configurations = new ArrayList<IvyConfiguration>();
    private List<IvyArtifact> artifacts = new ArrayList<IvyArtifact>();
    private List<DefaultIvyDependency> dependencies = new ArrayList<DefaultIvyDependency>();

    public IvyDescriptorFileGenerator(IvyPublicationIdentity projectIdentity) {
        this.projectIdentity = projectIdentity;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public IvyDescriptorFileGenerator addConfiguration(IvyConfiguration ivyConfiguration) {
        configurations.add(ivyConfiguration);
        return this;
    }

    public IvyDescriptorFileGenerator addArtifact(IvyArtifact ivyArtifact) {
        artifacts.add(ivyArtifact);
        return this;
    }

    public IvyDescriptorFileGenerator addDependency(IvyDependency ivyDependency) {
        dependencies.add((DefaultIvyDependency) ivyDependency);
        return this;
    }

    public IvyDescriptorFileGenerator withXml(final Action<XmlProvider> action) {
        xmlTransformer.addAction(action);
        return this;
    }

    public IvyDescriptorFileGenerator writeTo(File file) {
        xmlTransformer.transform(file, IVY_FILE_ENCODING, new Action<Writer>() {
            public void execute(Writer writer) {
                try {
                    writeDescriptor(writer);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
        return this;
    }

    private void writeDescriptor(final Writer writer) throws IOException {
        OptionalAttributeXmlWriter xmlWriter = new OptionalAttributeXmlWriter(writer, "  ", IVY_FILE_ENCODING);
        xmlWriter.startElement("ivy-module").attribute("version", "2.0");
        if (hasClassifier()) {
            xmlWriter.attribute("xmlns:m", "http://ant.apache.org/ivy/maven");
        }

        xmlWriter.startElement("info")
                .attribute("organisation", projectIdentity.getOrganisation())
                .attribute("module", projectIdentity.getModule())
                .attribute("revision", projectIdentity.getRevision())
                .attribute("status", status)
                .attribute("publication", ivyDateFormat.format(new Date()))
                .endElement();

        writeConfigurations(xmlWriter);
        writePublications(xmlWriter);
        writeDependencies(xmlWriter);
        xmlWriter.endElement();
    }
    private boolean hasClassifier() {
        for (IvyArtifact artifact : artifacts) {
            if (artifact.getClassifier() != null) {
                return true;
            }
        }
        for (DefaultIvyDependency dependency : this.dependencies) {
            for (DependencyArtifact dependencyArtifact : dependency.getModuleDependency().getArtifacts()) {
                if (dependencyArtifact.getClassifier() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private void writeConfigurations(OptionalAttributeXmlWriter xmlWriter) throws IOException {
        xmlWriter.startElement("configurations");
        for (IvyConfiguration configuration : configurations) {
            xmlWriter.startElement("conf")
                    .attribute("name", configuration.getName())
                    .attribute("visibility", "public");
            if (configuration.getExtends().size() > 0) {
                xmlWriter.attribute("extends", CollectionUtils.join(",", configuration.getExtends()));
            }
            xmlWriter.endElement();
        }
        xmlWriter.endElement();
    }

    private void writePublications(OptionalAttributeXmlWriter xmlWriter) throws IOException {
        xmlWriter.startElement("publications");
        for (IvyArtifact artifact : artifacts) {
            xmlWriter.startElement("artifact")
                    .attribute("name", artifact.getName())
                    .attribute("type", artifact.getType())
                    .attribute("ext", artifact.getExtension())
                    .attribute("conf", artifact.getConf())
                    .attribute("m:classifier", artifact.getClassifier())
                    .endElement();
        }
        xmlWriter.endElement();
    }

    private void writeDependencies(OptionalAttributeXmlWriter xmlWriter) throws IOException {
        xmlWriter.startElement("dependencies");
        for (DefaultIvyDependency dependency : dependencies) {
            ModuleDependency dep = dependency.getModuleDependency();
            xmlWriter.startElement("dependency")
                    .attribute("org", dep.getGroup())
                    .attribute("name", getDependencyName(dep))
                    .attribute("rev", dep.getVersion())
                    .attribute("conf", dependency.getConfMapping());

            for (DependencyArtifact dependencyArtifact : dep.getArtifacts()) {
                printDependencyArtifact(dependencyArtifact, xmlWriter);
            }
            xmlWriter.endElement();
        }
        xmlWriter.endElement();
    }

    private String getDependencyName(ModuleDependency dep) {
        return dep instanceof ProjectDependency ? ((ProjectDependency) dep).getDependencyProject().getName() : dep.getName();
    }

    private void printDependencyArtifact(DependencyArtifact dependencyArtifact, OptionalAttributeXmlWriter xmlWriter) throws IOException {
        // TODO Use IvyArtifact here
        xmlWriter.startElement("artifact")
                .attribute("name", dependencyArtifact.getName())
                .attribute("type", dependencyArtifact.getType())
                .attribute("ext", dependencyArtifact.getExtension())
                .attribute("m:classifier", dependencyArtifact.getClassifier())
                .endElement();
    }

    private static class OptionalAttributeXmlWriter extends SimpleXmlWriter {
        public OptionalAttributeXmlWriter(Writer writer, String indent, String encoding) throws IOException {
            super(writer, indent, encoding);
        }

        @Override
        public OptionalAttributeXmlWriter startElement(String name) throws IOException {
            super.startElement(name);
            return this;
        }

        @Override
        public OptionalAttributeXmlWriter attribute(String name, String value) throws IOException {
            if (value != null) {
                super.attribute(name, value);
            }
            return this;
        }

        public OptionalAttributeXmlWriter attribute(String name, String value, String defaultValue) throws IOException {
            super.attribute(name, value == null ? defaultValue : value);
            return this;
        }
    }
}
