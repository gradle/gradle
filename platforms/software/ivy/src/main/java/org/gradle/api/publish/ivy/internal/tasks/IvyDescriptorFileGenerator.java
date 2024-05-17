/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.tasks;

import com.google.common.base.Joiner;
import com.google.common.collect.Streams;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.IvyConfiguration;
import org.gradle.api.publish.ivy.IvyModuleDescriptorAuthor;
import org.gradle.api.publish.ivy.IvyModuleDescriptorDescription;
import org.gradle.api.publish.ivy.IvyModuleDescriptorLicense;
import org.gradle.api.publish.ivy.internal.artifact.IvyArtifactInternal;
import org.gradle.api.publish.ivy.internal.artifact.NormalizedIvyArtifact;
import org.gradle.api.publish.ivy.internal.dependency.IvyDependency;
import org.gradle.api.publish.ivy.internal.dependency.IvyExcludeRule;
import org.gradle.api.publish.ivy.internal.publication.IvyModuleDescriptorSpecInternal;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationCoordinates;
import org.gradle.internal.xml.SimpleXmlWriter;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.util.internal.CollectionUtils;

import javax.annotation.Nullable;
import javax.xml.namespace.QName;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class IvyDescriptorFileGenerator {

    private static final String IVY_FILE_ENCODING = "UTF-8";
    private static final String IVY_DATE_PATTERN = "yyyyMMddHHmmss";

    private IvyDescriptorFileGenerator() {}

    private static class Model {
        private String branch;
        private String status;
        private String organisation;
        private String module;
        private String revision;
        private final List<IvyModuleDescriptorLicense> licenses = new ArrayList<>();
        private final List<IvyModuleDescriptorAuthor> authors = new ArrayList<>();
        private IvyModuleDescriptorDescription description;
        private Map<QName, String> extraInfo;
        private final List<IvyConfiguration> configurations = new ArrayList<>();
        private final List<NormalizedIvyArtifact> artifacts = new ArrayList<>();
        private final List<IvyDependency> dependencies = new ArrayList<>();
        private final List<IvyExcludeRule> globalExcludes = new ArrayList<>();
    }

    public static DescriptorFileSpec generateSpec(IvyModuleDescriptorSpecInternal descriptor) {
        Model model = new Model();

        IvyPublicationCoordinates coordinates = descriptor.getCoordinates();
        model.organisation = coordinates.getOrganisation().get();
        model.module = coordinates.getModule().get();
        model.revision = coordinates.getRevision().get();

        model.status = descriptor.getStatus();
        model.branch = descriptor.getBranch();
        model.extraInfo = descriptor.getExtraInfo().asMap();
        model.description = descriptor.getDescription();

        model.authors.addAll(descriptor.getAuthors());
        model.licenses.addAll(descriptor.getLicenses());
        model.configurations.addAll(descriptor.getConfigurations().get());

        Set<IvyExcludeRule> globalExcludes = descriptor.getGlobalExcludes().getOrNull();
        if (globalExcludes != null) {
            model.globalExcludes.addAll(globalExcludes);
        }

        Set<IvyDependency> dependencies = descriptor.getDependencies().getOrNull();
        if (dependencies != null) {
            model.dependencies.addAll(dependencies);
        }

        for (IvyArtifact ivyArtifact : descriptor.getArtifacts().get()) {
            model.artifacts.add(((IvyArtifactInternal) ivyArtifact).asNormalisedArtifact());
        }

        XmlTransformer xmlTransformer = new XmlTransformer();
        xmlTransformer.addAction(descriptor.getXmlAction());
        if (descriptor.getWriteGradleMetadataMarker().get()) {
            xmlTransformer.addFinalizer(SerializableLambdas.action(IvyDescriptorFileGenerator::insertGradleMetadataMarker));
        }

        return new DescriptorFileSpec(model, xmlTransformer);
    }

    public static class ModelWriter {
        private final SimpleDateFormat ivyDateFormat = new SimpleDateFormat(IVY_DATE_PATTERN);
        private final Model model;

        public ModelWriter(Model model) {
            this.model = model;
        }

        private void writeDescriptor(final Writer writer) throws IOException {
            OptionalAttributeXmlWriter xmlWriter = new OptionalAttributeXmlWriter(writer, "  ", IVY_FILE_ENCODING);
            xmlWriter.startElement("ivy-module").attribute("version", "2.0");
            if (usesClassifier(model)) {
                xmlWriter.attribute("xmlns:m", "http://ant.apache.org/ivy/maven");
            }

            xmlWriter.startElement("info")
                .attribute("organisation", model.organisation)
                .attribute("module", model.module)
                .attribute("branch", model.branch)
                .attribute("revision", model.revision)
                .attribute("status", model.status)
                .attribute("publication", ivyDateFormat.format(new Date()));

            for (IvyModuleDescriptorLicense license : model.licenses) {
                xmlWriter.startElement("license")
                    .attribute("name", license.getName().getOrNull())
                    .attribute("url", license.getUrl().getOrNull())
                    .endElement();
            }

            for (IvyModuleDescriptorAuthor author : model.authors) {
                xmlWriter.startElement("ivyauthor")
                    .attribute("name", author.getName().getOrNull())
                    .attribute("url", author.getUrl().getOrNull())
                    .endElement();
            }

            if (model.description != null) {
                xmlWriter.startElement("description")
                    .attribute("homepage", model.description.getHomepage().getOrNull())
                    .characters(model.description.getText().getOrElse(""))
                    .endElement();
            }

            if (model.extraInfo != null) {
                for (Map.Entry<QName, String> entry : model.extraInfo.entrySet()) {
                    if (entry.getKey() != null) {
                        xmlWriter.startElement("ns:" + entry.getKey().getLocalPart())
                            .attribute("xmlns:ns", entry.getKey().getNamespaceURI())
                            .characters(entry.getValue())
                            .endElement();
                    }
                }
            }

            xmlWriter.endElement();

            writeConfigurations(xmlWriter);
            writePublications(xmlWriter);
            writeDependencies(xmlWriter);
            xmlWriter.endElement();
        }

        private void writeConfigurations(OptionalAttributeXmlWriter xmlWriter) throws IOException {
            xmlWriter.startElement("configurations");
            for (IvyConfiguration configuration : model.configurations) {
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
            for (IvyArtifact artifact : model.artifacts) {
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
            for (IvyDependency dependency : model.dependencies) {
                String org = dependency.getOrganisation();
                String module = dependency.getModule();

                xmlWriter.startElement("dependency")
                    .attribute("org", org)
                    .attribute("name", module)
                    .attribute("rev", dependency.getRevision())
                    .attribute("conf", dependency.getConfMapping())
                    .attribute("revConstraint", dependency.getRevConstraint());

                if (!dependency.isTransitive()) {
                    xmlWriter.attribute("transitive", "false");
                }

                for (DependencyArtifact dependencyArtifact : dependency.getArtifacts()) {
                    writeDependencyArtifact(dependencyArtifact, xmlWriter);
                }
                for (ExcludeRule excludeRule : dependency.getExcludeRules()) {
                    writeDependencyExclude(excludeRule, xmlWriter);
                }
                xmlWriter.endElement();
            }
            for (IvyExcludeRule excludeRule : model.globalExcludes) {
                writeGlobalExclude(excludeRule, xmlWriter);
            }
            xmlWriter.endElement();
        }

        private static void writeDependencyExclude(ExcludeRule excludeRule, OptionalAttributeXmlWriter xmlWriter) throws IOException {
            xmlWriter.startElement("exclude")
                .attribute("org", excludeRule.getGroup())
                .attribute("module", excludeRule.getModule())
                .endElement();
        }

        private static void writeDependencyArtifact(DependencyArtifact dependencyArtifact, OptionalAttributeXmlWriter xmlWriter) throws IOException {
            // TODO Use IvyArtifact here
            xmlWriter.startElement("artifact")
                .attribute("name", dependencyArtifact.getName())
                .attribute("type", dependencyArtifact.getType())
                .attribute("ext", dependencyArtifact.getExtension())
                .attribute("m:classifier", dependencyArtifact.getClassifier())
                .endElement();
        }

        private static void writeGlobalExclude(IvyExcludeRule excludeRule, OptionalAttributeXmlWriter xmlWriter) throws IOException {
            xmlWriter.startElement("exclude")
                .attribute("org", excludeRule.getOrg())
                .attribute("module", excludeRule.getModule())
                .attribute("conf", excludeRule.getConf())
                .endElement();
        }

        private static boolean usesClassifier(Model model) {
            for (IvyArtifact artifact : model.artifacts) {
                if (artifact.getClassifier() != null) {
                    return true;
                }
            }
            for (IvyDependency dependency : model.dependencies) {
                for (DependencyArtifact dependencyArtifact : dependency.getArtifacts()) {
                    if (dependencyArtifact.getClassifier() != null) {
                        return true;
                    }
                }
            }
            return false;
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
            public OptionalAttributeXmlWriter attribute(String name, @Nullable String value) throws IOException {
                if (value != null) {
                    super.attribute(name, value);
                }
                return this;
            }

            @Override
            public OptionalAttributeXmlWriter comment(String comment) throws IOException {
                super.comment(comment);
                return this;
            }
        }
    }

    public static void insertGradleMetadataMarker(XmlProvider xmlProvider) {
        String comment = Joiner.on("").join(
            Streams.concat(
                Arrays.stream(MetaDataParser.GRADLE_METADATA_MARKER_COMMENT_LINES),
                Stream.of(MetaDataParser.GRADLE_6_METADATA_MARKER)
            )
            .map(content -> "<!-- " + content + " -->\n  ")
            .iterator()
        );

        StringBuilder builder = xmlProvider.asString();
        int idx = builder.indexOf("<info");
        builder.insert(idx, comment);
    }

    public static class DescriptorFileSpec {

        private final Model model;
        private final XmlTransformer xmlTransformer;

        public DescriptorFileSpec(Model model, XmlTransformer xmlTransformer) {
            this.model = model;
            this.xmlTransformer = xmlTransformer;
        }

        public void writeTo(File destination) {
            xmlTransformer.transform(destination, IVY_FILE_ENCODING, writer -> {
                try {
                    new ModelWriter(model).writeDescriptor(writer);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
