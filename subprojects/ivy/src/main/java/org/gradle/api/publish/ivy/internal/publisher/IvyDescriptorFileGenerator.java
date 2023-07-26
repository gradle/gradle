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

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ExactVersionSelector;
import org.gradle.api.publish.internal.versionmapping.VariantVersionMappingStrategyInternal;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.IvyConfiguration;
import org.gradle.api.publish.ivy.IvyModuleDescriptorAuthor;
import org.gradle.api.publish.ivy.IvyModuleDescriptorDescription;
import org.gradle.api.publish.ivy.IvyModuleDescriptorLicense;
import org.gradle.api.publish.ivy.internal.dependency.IvyDependencyInternal;
import org.gradle.api.publish.ivy.internal.dependency.IvyExcludeRule;
import org.gradle.internal.xml.SimpleXmlWriter;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.util.Path;
import org.gradle.util.internal.CollectionUtils;

import javax.xml.namespace.QName;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class IvyDescriptorFileGenerator {
    private static final String IVY_FILE_ENCODING = "UTF-8";
    private static final String IVY_DATE_PATTERN = "yyyyMMddHHmmss";
    private static final Action<XmlProvider> ADD_GRADLE_METADATA_MARKER = new Action<XmlProvider>() {
        @Override
        public void execute(XmlProvider xmlProvider) {
            StringBuilder builder = xmlProvider.asString();
            int idx = builder.indexOf("<info");
            builder.insert(idx, xmlComments(MetaDataParser.GRADLE_METADATA_MARKER_COMMENT_LINES)
                + "  "
                + xmlComment(MetaDataParser.GRADLE_6_METADATA_MARKER)
                + "  ");
        }
    };

    private final VersionMappingStrategyInternal versionMappingStrategy;
    private final XmlTransformer xmlTransformer = new XmlTransformer();
    private final Model model;

    static class Model {
        private String branch;
        private String status;
        private final String organisation;
        private final String module;
        private final String revision;
        private final List<IvyModuleDescriptorLicense> licenses = new ArrayList<IvyModuleDescriptorLicense>();
        private final List<IvyModuleDescriptorAuthor> authors = new ArrayList<IvyModuleDescriptorAuthor>();
        private IvyModuleDescriptorDescription description;
        private Map<QName, String> extraInfo;
        private final List<IvyConfiguration> configurations = new ArrayList<IvyConfiguration>();
        private final List<NormalizedIvyArtifact> artifacts = new ArrayList<>();
        private final List<IvyDependencyInternal> dependencies = new ArrayList<IvyDependencyInternal>();
        private final Map<IvyDependencyInternal, ModuleVersionIdentifier> resolvedVersions = new LinkedHashMap<>();
        private List<IvyExcludeRule> globalExcludes = new ArrayList<IvyExcludeRule>();

        public Model(IvyPublicationIdentity projectIdentity) {
            organisation = projectIdentity.getOrganisation();
            module = projectIdentity.getModule();
            revision = projectIdentity.getRevision();
        }

        private boolean usesClassifier() {
            for (IvyArtifact artifact : artifacts) {
                if (artifact.getClassifier() != null) {
                    return true;
                }
            }
            for (IvyDependencyInternal dependency : dependencies) {
                for (DependencyArtifact dependencyArtifact : dependency.getArtifacts()) {
                    if (dependencyArtifact.getClassifier() != null) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
    public IvyDescriptorFileGenerator(IvyPublicationIdentity projectIdentity, boolean writeGradleRedirectionMarker, VersionMappingStrategyInternal versionMappingStrategy) {
        model = new Model(projectIdentity);
        this.versionMappingStrategy = versionMappingStrategy;
        if (writeGradleRedirectionMarker) {
            xmlTransformer.addFinalizer(ADD_GRADLE_METADATA_MARKER);
        }
    }

    public void setStatus(String status) {
        model.status = status;
    }

    public void setBranch(String branch) {
        model.branch = branch;
    }

    public IvyDescriptorFileGenerator addLicense(IvyModuleDescriptorLicense ivyLicense) {
        model.licenses.add(ivyLicense);
        return this;
    }

    public IvyDescriptorFileGenerator addAuthor(IvyModuleDescriptorAuthor ivyAuthor) {
        model.authors.add(ivyAuthor);
        return this;
    }

    public void setDescription(IvyModuleDescriptorDescription ivyDescription) {
        model.description = ivyDescription;
    }

    public Map<QName, String> getExtraInfo() {
        return model.extraInfo;
    }

    public void setExtraInfo(Map<QName, String> extraInfo) {
        model.extraInfo = extraInfo;
    }

    public IvyDescriptorFileGenerator addConfiguration(IvyConfiguration ivyConfiguration) {
        model.configurations.add(ivyConfiguration);
        return this;
    }

    public IvyDescriptorFileGenerator addArtifact(NormalizedIvyArtifact ivyArtifact) {
        model.artifacts.add(ivyArtifact);
        return this;
    }

    public IvyDescriptorFileGenerator addDependency(IvyDependencyInternal ivyDependency) {
        model.dependencies.add(ivyDependency);
        VariantVersionMappingStrategyInternal variantStrategy = versionMappingStrategy.findStrategyForVariant(ivyDependency.getAttributes());
        String org = ivyDependency.getOrganisation();
        String module = ivyDependency.getModule();
        Path identityPath = ivyDependency.getProjectIdentityPath();
        ModuleVersionIdentifier resolvedVersion = variantStrategy.maybeResolveVersion(org, module, identityPath);
        if (resolvedVersion != null) {
            model.resolvedVersions.put(ivyDependency, resolvedVersion);
        }
        return this;
    }

    public IvyDescriptorFileGenerator addGlobalExclude(IvyExcludeRule excludeRule) {
        model.globalExcludes.add(excludeRule);
        return this;
    }

    public IvyDescriptorFileGenerator withXml(final Action<XmlProvider> action) {
        xmlTransformer.addAction(action);
        return this;
    }

    public IvyDescriptorFileGenerator writeTo(File file) {
        toSpec().writeTo(file);
        return this;
    }

    public static class ModelWriter {
        private final SimpleDateFormat ivyDateFormat = new SimpleDateFormat(IVY_DATE_PATTERN);
        private final Model model;

        public ModelWriter(Model model) {
            this.model = model;
        }

        private void writeTo(File file, XmlTransformer xmlTransformer) {
            xmlTransformer.transform(file, IVY_FILE_ENCODING, new Action<Writer>() {
                @Override
                public void execute(Writer writer) {
                    try {
                        writeDescriptor(writer);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        }

        private void writeDescriptor(final Writer writer) throws IOException {
            OptionalAttributeXmlWriter xmlWriter = new OptionalAttributeXmlWriter(writer, "  ", IVY_FILE_ENCODING);
            xmlWriter.startElement("ivy-module").attribute("version", "2.0");
            if (model.usesClassifier()) {
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
            for (IvyDependencyInternal dependency : model.dependencies) {
                String org = dependency.getOrganisation();
                String module = dependency.getModule();
                ModuleVersionIdentifier resolvedVersion = model.resolvedVersions.get(dependency);
                if (resolvedVersion != null) {
                    org = resolvedVersion.getGroup();
                    module = resolvedVersion.getName();
                }
                xmlWriter.startElement("dependency")
                    .attribute("org", org)
                    .attribute("name", module)
                    .attribute("rev", resolvedVersion != null ? resolvedVersion.getVersion() : dependency.getRevision())
                    .attribute("conf", dependency.getConfMapping());


                if (resolvedVersion != null && isDynamicVersion(dependency.getRevision())) {
                    xmlWriter.attribute("revConstraint", dependency.getRevision());
                }

                if (!dependency.isTransitive()) {
                    xmlWriter.attribute("transitive", "false");
                }

                for (DependencyArtifact dependencyArtifact : dependency.getArtifacts()) {
                    printDependencyArtifact(dependencyArtifact, xmlWriter);
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

        private boolean isDynamicVersion(String version) {
            return !ExactVersionSelector.isExact(version);
        }

        private void writeDependencyExclude(ExcludeRule excludeRule, OptionalAttributeXmlWriter xmlWriter) throws IOException {
            xmlWriter.startElement("exclude")
                .attribute("org", excludeRule.getGroup())
                .attribute("module", excludeRule.getModule())
                .endElement();
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

        private void writeGlobalExclude(IvyExcludeRule excludeRule, OptionalAttributeXmlWriter xmlWriter) throws IOException {
            xmlWriter.startElement("exclude")
                .attribute("org", excludeRule.getOrg())
                .attribute("module", excludeRule.getModule())
                .attribute("conf", excludeRule.getConf())
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

            @Override
            public OptionalAttributeXmlWriter comment(String comment) throws IOException {
                super.comment(comment);
                return this;
            }
        }
    }

    public DescriptorFileSpec toSpec() {
        return new DescriptorFileSpec(model, xmlTransformer);
    }

    public static class DescriptorFileSpec {
        private Model model;
        private XmlTransformer xmlTransformer;

        public DescriptorFileSpec(Model model, XmlTransformer xmlTransformer) {
            this.model = model;
            this.xmlTransformer = xmlTransformer;
        }

        public void writeTo(File destination) {
            new ModelWriter(model).writeTo(destination, xmlTransformer);
        }
    }

    private static String xmlComments(String[] lines) {
        return Joiner.on("  ").join(Iterables.transform(Arrays.asList(lines), IvyDescriptorFileGenerator::xmlComment));
    }

    private static String xmlComment(String content) {
        return "<!-- " + content + " -->\n";
    }
}
