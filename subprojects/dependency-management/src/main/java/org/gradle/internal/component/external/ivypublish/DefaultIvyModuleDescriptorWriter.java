/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.component.external.ivypublish;

import com.google.common.base.Joiner;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.internal.Pair;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.xml.SimpleXmlWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class DefaultIvyModuleDescriptorWriter implements IvyModuleDescriptorWriter {
    public static final String IVY_DATE_PATTERN = "yyyyMMddHHmmss";

    private final ComponentSelectorConverter componentSelectorConverter;

    public DefaultIvyModuleDescriptorWriter(ComponentSelectorConverter componentSelectorConverter) {
        this.componentSelectorConverter = componentSelectorConverter;
    }

    @Override
    public void write(IvyModulePublishMetadata module, File output) {
        try {
            output.getParentFile().mkdirs();
            try (OutputStream outputStream = new FileOutputStream(output)) {
                SimpleXmlWriter xmlWriter = new SimpleXmlWriter(outputStream, "  ");
                writeTo(module, xmlWriter);
                xmlWriter.flush();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeTo(IvyModulePublishMetadata metadata, SimpleXmlWriter writer) throws IOException {
        writer.startElement("ivy-module");
        writer.attribute("version", "2.0");

        writer.attribute("xmlns:" + IvyModulePublishMetadata.IVY_MAVEN_NAMESPACE_PREFIX, IvyModulePublishMetadata.IVY_MAVEN_NAMESPACE);

        printInfoTag(metadata, writer);
        printConfigurations(metadata, writer);
        printPublications(metadata.getArtifacts(), writer);
        printDependencies(metadata, writer);

        writer.endElement();
    }

    private static void printInfoTag(IvyModulePublishMetadata metadata, SimpleXmlWriter writer) throws IOException {
        ModuleComponentIdentifier id = metadata.getComponentId();
        writer.startElement("info");

        writer.attribute("organisation", id.getGroup());
        writer.attribute("module", id.getModule());
        writer.attribute("revision", id.getVersion());
        writer.attribute("status", metadata.getStatus());

        printUnusedContent(metadata, writer);

        writer.endElement();
    }

    /**
     * These values are written to the descriptor, but never used by Gradle.
     */
    private static void printUnusedContent(IvyModulePublishMetadata metadata, SimpleXmlWriter writer) throws IOException {
        SimpleDateFormat ivyDateFormat = new SimpleDateFormat(IVY_DATE_PATTERN);
        writer.attribute("publication", ivyDateFormat.format(new Date()));
    }

    private void printConfigurations(IvyModulePublishMetadata metadata, SimpleXmlWriter writer) throws IOException {
        Collection<Configuration> configurations = metadata.getConfigurations().values();
        if (!configurations.isEmpty()) {
            writer.startElement("configurations");
            for (Configuration configuration : configurations) {
                printConfiguration(configuration, writer);
            }
            writer.endElement();
        }
    }

    private void printConfiguration(Configuration conf, SimpleXmlWriter writer) throws IOException {
        writer.startElement("conf");
        writer.attribute("name", conf.getName());
        writer.attribute("visibility", conf.isVisible() ? "public" : "private");
        List<String> exts = conf.getExtendsFrom();
        if (exts.size() > 0) {
            writer.attribute("extends", Joiner.on(',').join(exts));
        }
        if (!conf.isTransitive()) {
            writer.attribute("transitive", "false");
        }
        writer.endElement();
    }

    private static void printPublications(Collection<IvyModuleArtifactPublishMetadata> artifacts, SimpleXmlWriter writer) throws IOException {
        writer.startElement("publications");
        for (IvyModuleArtifactPublishMetadata artifactMetadata : artifacts) {
            IvyArtifactName artifact = artifactMetadata.getArtifactName();
            writer.startElement("artifact");
            writer.attribute("name", artifact.getName());
            writer.attribute("type", artifact.getType());
            writer.attribute("ext", artifact.getExtension() == null ? "" : artifact.getExtension());
            writer.attribute("conf", Joiner.on(",").join(artifactMetadata.getConfigurations()));
            if (artifact.getClassifier() != null) {
                printExtraAttributes(Collections.singletonMap("m:classifier", artifact.getClassifier()), writer);
            }
            writer.endElement();
        }
        writer.endElement();
    }

    private void printDependencies(IvyModulePublishMetadata metadata, SimpleXmlWriter writer) throws IOException {
        Collection<LocalOriginDependencyMetadata> dependencies = metadata.getDependencies();
        if (dependencies.size() > 0) {
            writer.startElement("dependencies");
            for (LocalOriginDependencyMetadata dd : dependencies) {
                printDependency(dd, writer);
            }
            printAllExcludes(metadata, writer);
            writer.endElement();
        }
    }

    protected void printDependency(LocalOriginDependencyMetadata dep, SimpleXmlWriter writer) throws IOException {
        writer.startElement("dependency");

        ModuleVersionSelector requested = componentSelectorConverter.getSelector(dep.getSelector());
        writer.attribute("org", requested.getGroup());
        writer.attribute("name", requested.getName());
        writer.attribute("rev", requested.getVersion());
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

        for (IvyArtifactName dependencyArtifact : dep.getArtifacts()) {
            printDependencyArtifact(writer, dependencyArtifact, dep.getModuleConfiguration());
        }

        printDependencyExcludeRules(writer, dep.getExcludes());

        writer.endElement();
    }

    private String getConfMapping(LocalOriginDependencyMetadata dependency) {
        return dependency.getModuleConfiguration() + "->" + dependency.getDependencyConfiguration();
    }

    private static void printAllExcludes(IvyModulePublishMetadata metadata, SimpleXmlWriter writer) throws IOException {
        for (Pair<ExcludeMetadata, String> excludePair : metadata.getExcludes()) {
            ExcludeMetadata exclude = excludePair.getLeft();
            writer.startElement("exclude");
            writer.attribute("org", exclude.getModuleId().getGroup());
            writer.attribute("module", exclude.getModuleId().getName());

            IvyArtifactName artifact = exclude.getArtifact();
            if (artifact != null) {
                writer.attribute("artifact", artifact.getName());
                writer.attribute("type", artifact.getType());
                writer.attribute("ext", artifact.getExtension());
            }
            writer.attribute("conf", excludePair.getRight());
            writer.endElement();
        }
    }

    private static void printDependencyExcludeRules(SimpleXmlWriter writer,
                                                    Collection<ExcludeMetadata> excludes) throws IOException {
        for (ExcludeMetadata exclude : excludes) {
            writer.startElement("exclude");
            writer.attribute("org", exclude.getModuleId().getGroup());
            writer.attribute("module", exclude.getModuleId().getName());

            IvyArtifactName artifact = exclude.getArtifact();
            if (artifact != null) {
                writer.attribute("name", artifact.getName());
                writer.attribute("type", artifact.getType());
                writer.attribute("ext", artifact.getExtension());
            }
            writer.endElement();
        }
    }

    private static void printDependencyArtifact(SimpleXmlWriter writer, IvyArtifactName artifact, String configuration) throws IOException {
        writer.startElement("artifact");
        writer.attribute("name", artifact.getName());
        writer.attribute("type", artifact.getType());
        writer.attribute("ext", artifact.getExtension());
        if (artifact.getClassifier() != null) {
            printExtraAttributes(Collections.singletonMap("m:classifier", artifact.getClassifier()), writer);
        }
        writer.attribute("conf", configuration);
        writer.endElement();
    }

    /**
     * Writes the specified <code>Map</code> containing the extra attributes to the given <code>PrintWriter</code>.
     *
     * @param extra the extra attributes, can be <code>null</code>
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
