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

package org.gradle.api.internal.artifacts.ivyservice;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.model.DefaultIvyModuleArtifactPublishMetaData;
import org.gradle.internal.component.external.model.IvyModuleArtifactPublishMetaData;
import org.gradle.internal.component.external.model.IvyModulePublishMetaData;
import org.gradle.internal.component.external.model.ModuleDescriptorState;
import org.gradle.internal.component.model.DefaultDependencyMetaData;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.xml.SimpleXmlWriter;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IvyXmlModuleDescriptorWriter implements IvyModuleDescriptorWriter {

    @Override
    public void write(ModuleDescriptorState md, File output) {
        final ModuleComponentIdentifier componentIdentifier = md.getComponentIdentifier();
        List<IvyModuleArtifactPublishMetaData> ivyArtifacts = CollectionUtils.collect(md.getArtifacts(), new Transformer<IvyModuleArtifactPublishMetaData, ModuleDescriptorState.Artifact>() {
            @Override
            public IvyModuleArtifactPublishMetaData transform(ModuleDescriptorState.Artifact artifact) {
                return new DefaultIvyModuleArtifactPublishMetaData(componentIdentifier, artifact.artifactName, artifact.configurations);
            }
        });
        doWrite(md, ivyArtifacts, output);
    }

    @Override
    public void write(ModuleDescriptorState descriptor, Collection<IvyModuleArtifactPublishMetaData> artifacts, File output) {
        doWrite(descriptor, artifacts, output);
    }

    private void doWrite(ModuleDescriptorState descriptor, Collection<IvyModuleArtifactPublishMetaData> artifacts, File output) {
        try {
            output.getParentFile().mkdirs();
            OutputStream outputStream = new FileOutputStream(output);
            try {
                SimpleXmlWriter xmlWriter = new SimpleXmlWriter(outputStream, "  ");
                writeTo(descriptor, artifacts, xmlWriter);
                xmlWriter.flush();
            } finally {
                outputStream.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeTo(ModuleDescriptorState descriptor, Collection<IvyModuleArtifactPublishMetaData> artifacts, SimpleXmlWriter writer) throws IOException {
        writer.startElement("ivy-module");
        writer.attribute("version", "2.0");

        writer.attribute("xmlns:" + IvyModulePublishMetaData.IVY_MAVEN_NAMESPACE_PREFIX, IvyModulePublishMetaData.IVY_MAVEN_NAMESPACE);

        printInfoTag(descriptor, writer);
        printConfigurations(descriptor, writer);
        printPublications(artifacts, writer);
        printDependencies(descriptor, writer);

        writer.endElement();
    }

    private static void printInfoTag(ModuleDescriptorState descriptor, SimpleXmlWriter writer) throws IOException {
        ModuleComponentIdentifier id = descriptor.getComponentIdentifier();
        writer.startElement("info");

        writer.attribute("organisation", id.getGroup());
        writer.attribute("module", id.getModule());
        String branch = descriptor.getBranch();
        if (branch != null) {
            writer.attribute("branch", branch);
        }
        writer.attribute("revision", id.getVersion());
        writer.attribute("status", descriptor.getStatus());

        if (descriptor.isDefault()) {
            writer.attribute("default", "true");
        }

        for (Map.Entry<NamespaceId, String> entry : descriptor.getExtraInfo().entrySet()) {
            if (entry.getValue() == null || entry.getValue().length() == 0) {
                continue;
            }
            NamespaceId namespaceId = entry.getKey();
            writer.startElement("ns:" + namespaceId.getName());
            writer.attribute("xmlns:ns", namespaceId.getNamespace());
            writer.characters(entry.getValue());
            writer.endElement();
        }

        writer.endElement();
    }

    private static void printConfigurations(ModuleDescriptorState descriptor, SimpleXmlWriter writer) throws IOException {
        List<ModuleDescriptorState.Configuration> configurations = descriptor.getConfigurations();
        if (!configurations.isEmpty()) {
            writer.startElement("configurations");
            for (ModuleDescriptorState.Configuration configuration : configurations) {
                printConfiguration(configuration, writer);
            }
            writer.endElement();
        }
    }

    private static void printConfiguration(ModuleDescriptorState.Configuration conf, SimpleXmlWriter writer) throws IOException {
        writer.startElement("conf");
        writer.attribute("name", conf.name);
        writer.attribute("visibility", conf.visible ? "public" : "private");
        List<String> exts = conf.extendsFrom;
        if (exts.size() > 0) {
            writer.attribute("extends", Joiner.on(',').join(exts));
        }
        if (!conf.transitive) {
            writer.attribute("transitive", "false");
        }
        writer.endElement();
    }

    private static void printPublications(Collection<IvyModuleArtifactPublishMetaData> artifacts, SimpleXmlWriter writer) throws IOException {
        writer.startElement("publications");
        for (IvyModuleArtifactPublishMetaData artifactMetadata : artifacts) {
            IvyArtifactName artifact = artifactMetadata.getArtifactName();
            writer.startElement("artifact");
            writer.attribute("name", artifact.getName());
            writer.attribute("type", artifact.getType());
            writer.attribute("ext", artifact.getExtension());
            writer.attribute("conf", Joiner.on(",").join(artifactMetadata.getConfigurations()));
            if (artifact.getClassifier() != null) {
                printExtraAttributes(Collections.singletonMap("m:classifier", artifact.getClassifier()), writer);
            }
            writer.endElement();
        }
        writer.endElement();
    }

    private void printDependencies(ModuleDescriptorState descriptor, SimpleXmlWriter writer) throws IOException {
        List<DependencyMetaData> dds = descriptor.getDependencies();
        if (dds.size() > 0) {
            writer.startElement("dependencies");
            for (DependencyMetaData dd : dds) {
                printDependency(descriptor, dd, writer);
            }
            printAllExcludes(descriptor, writer);
            writer.endElement();
        }
    }

    protected void printDependency(ModuleDescriptorState descriptor, DependencyMetaData dep,
                                   SimpleXmlWriter writer) throws IOException {
        writer.startElement("dependency");

        ModuleVersionSelector requested = dep.getRequested();
        writer.attribute("org", requested.getGroup());
        writer.attribute("name", requested.getName());
        writer.attribute("rev", requested.getVersion());
        if (dep.getDynamicConstraintVersion() != null && !dep.getDynamicConstraintVersion().equals(requested.getVersion())) {
            writer.attribute("revConstraint", dep.getDynamicConstraintVersion());
        }
        if (dep.isForce()) {
            writer.attribute("force", "true");
        }
        if (dep.isChanging()) {
            writer.attribute("changing", "true");
        }
        if (!dep.isTransitive()) {
            writer.attribute("transitive", "false");
        }
        writer.attribute("conf", getConfMapping(dep));

        Map<IvyArtifactName, Set<String>> depArtifacts = ((DefaultDependencyMetaData) dep).getArtifactMappings();
        for (Map.Entry<IvyArtifactName, Set<String>> artifactEntry : depArtifacts.entrySet()) {
            printDependencyArtifact(descriptor, writer, artifactEntry.getKey(), artifactEntry.getValue());
        }

        Set<ExcludeRule> excludes = ((DefaultDependencyMetaData) dep).getAllExcludeRules();
        printDependencyExcludeRules(descriptor, writer, excludes);

        writer.endElement();
    }

    private String getConfMapping(DependencyMetaData dependency) {

        Map<String, List<String>> configMappings = ((DefaultDependencyMetaData) dependency).getConfigMappings();

        StringBuilder confs = new StringBuilder();
        String delimiter = "";
        for (String modConf : configMappings.keySet()) {
            confs.append(delimiter);
            confs.append(modConf).append("->");

            List<String> depConfs = configMappings.get(modConf);
            Joiner.on(",").appendTo(confs, depConfs);

            delimiter = ";";
        }
        return confs.toString();
    }

    private static void printAllExcludes(ModuleDescriptorState descriptor, SimpleXmlWriter writer) throws IOException {
        List<ExcludeRule> excludes = descriptor.getExcludeRules();
        for (ExcludeRule exclude : excludes) {
            writer.startElement("exclude");
            writer.attribute("org", exclude.getId().getModuleId().getOrganisation());
            writer.attribute("module", exclude.getId().getModuleId().getName());
            writer.attribute("artifact", exclude.getId().getName());
            writer.attribute("type", exclude.getId().getType());
            writer.attribute("ext", exclude.getId().getExt());
            String[] ruleConfs = exclude.getConfigurations();
            if (!descriptor.getConfigurationsNames().equals(Arrays.asList(ruleConfs))) {
                writer.attribute("conf", Joiner.on(',').join(ruleConfs));
            }
            writer.attribute("matcher", exclude.getMatcher().getName());
            writer.endElement();
        }
    }

    private static void printDependencyExcludeRules(ModuleDescriptorState descriptor, SimpleXmlWriter writer,
                                                    Set<ExcludeRule> excludes) throws IOException {
        for (ExcludeRule exclude : excludes) {
            writer.startElement("exclude");
            writer.attribute("org", exclude.getId().getModuleId().getOrganisation());
            writer.attribute("module", exclude.getId().getModuleId().getName());
            writer.attribute("name", exclude.getId().getName());
            writer.attribute("type", exclude.getId().getType());
            writer.attribute("ext", exclude.getId().getExt());
            String[] ruleConfs = exclude.getConfigurations();
            if (!descriptor.getConfigurationsNames().equals(Arrays.asList(ruleConfs))) {
                writer.attribute("conf", Joiner.on(',').join(ruleConfs));
            }
            writer.attribute("matcher", exclude.getMatcher().getName());
            writer.endElement();
        }
    }

    private static void printDependencyArtifact(ModuleDescriptorState descriptor, SimpleXmlWriter writer,
                                                IvyArtifactName artifact, Set<String> configurations) throws IOException {
        writer.startElement("artifact");
        writer.attribute("name", artifact.getName());
        writer.attribute("type", artifact.getType());
        writer.attribute("ext", artifact.getExtension());
        if (artifact.getClassifier() != null) {
            printExtraAttributes(Collections.singletonMap("m:classifier", artifact.getClassifier()), writer);
        }
        if (!Sets.newHashSet(descriptor.getConfigurationsNames()).equals(configurations)) {
            writer.attribute("conf", Joiner.on(',').join(configurations));
        }
        writer.endElement();
    }

    /**
     * Writes the specified <tt>Map</tt> containing the extra attributes to the given <tt>PrintWriter</tt>.
     *
     * @param extra the extra attributes, can be <tt>null</tt>
     * @param writer the writer to use
     */
    private static void printExtraAttributes(Map<String, ?> extra, SimpleXmlWriter writer) throws IOException {
        if (extra == null) {
            return;
        }
        for (Map.Entry<String, ?> entry : extra.entrySet()) {
            writer.attribute(entry.getKey(), entry.getValue().toString());
        }
    }
}
