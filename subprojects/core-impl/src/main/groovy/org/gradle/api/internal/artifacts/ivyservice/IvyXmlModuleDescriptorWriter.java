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
import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.util.extendable.ExtendableItem;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.xml.SimpleXmlWriter;
import org.gradle.internal.UncheckedException;

import java.io.*;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.*;

public class IvyXmlModuleDescriptorWriter implements IvyModuleDescriptorWriter {
    public static final String IVY_DATE_PATTERN = "yyyyMMddHHmmss";
    private final Field dependencyConfigField;

    public IvyXmlModuleDescriptorWriter() {
        try {
            dependencyConfigField = DefaultDependencyDescriptor.class.getDeclaredField("confs");
        } catch (NoSuchFieldException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        dependencyConfigField.setAccessible(true);
    }

    private void writeTo(ModuleDescriptor md, SimpleXmlWriter writer) throws IOException {
        writer.startElement("ivy-module");
        writer.attribute("version", "2.0");

        Map<String, String> namespaces = md.getExtraAttributesNamespaces();
        for (Map.Entry<String, String> entry : namespaces.entrySet()) {
            writer.attribute("xmlns:" + entry.getKey(), entry.getValue());
        }

        printInfoTag(md, writer);
        printConfigurations(md, writer);
        printPublications(md, writer);
        printDependencies(md, writer);

        writer.endElement();
    }

    public void write(ModuleDescriptor md, File output) {
        try {
            output.getParentFile().mkdirs();
            OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(output));
            try {
                SimpleXmlWriter xmlWriter = new SimpleXmlWriter(outputStream, "  ");
                writeTo(md, xmlWriter);
                xmlWriter.flush();
            } finally {
                outputStream.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void printDependencies(ModuleDescriptor md, SimpleXmlWriter writer) throws IOException {
        DependencyDescriptor[] dds = md.getDependencies();
        if (dds.length > 0) {
            writer.startElement("dependencies");
            for (int i = 0; i < dds.length; i++) {
                DependencyDescriptor dep = dds[i];
                printDependency(md, dep, writer);
            }
            printAllExcludes(md, writer);
            writer.endElement();
        }
    }

    protected void printDependency(ModuleDescriptor md, DependencyDescriptor dep,
                                          SimpleXmlWriter writer) throws IOException {
        writer.startElement("dependency");

        ModuleRevisionId dependencyRevisionId = dep.getDependencyRevisionId();
        writer.attribute("org", dependencyRevisionId.getOrganisation());
        writer.attribute("name", dependencyRevisionId.getName());
        if (dependencyRevisionId.getBranch() != null) {
            writer.attribute("branch", dependencyRevisionId.getBranch());
        }
        writer.attribute("rev", dependencyRevisionId.getRevision());
        if (!dep.getDynamicConstraintDependencyRevisionId().equals(dependencyRevisionId)) {
            if (dep.getDynamicConstraintDependencyRevisionId().getBranch() != null) {
                writer.attribute("branchConstraint", dep.getDynamicConstraintDependencyRevisionId().getBranch());
            }
            writer.attribute("revConstraint", dep.getDynamicConstraintDependencyRevisionId().getRevision());
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

        printExtraAttributes(dep, writer);

        DependencyArtifactDescriptor[] depArtifacts = dep.getAllDependencyArtifacts();
        printDependencyArtefacts(md, writer, depArtifacts);

        IncludeRule[] includes = dep.getAllIncludeRules();
        printDependencyIncludeRules(md, writer, includes);

        ExcludeRule[] excludes = dep.getAllExcludeRules();
        printDependencyExcludeRules(md, writer, excludes);

        writer.endElement();
    }

    private String getConfMapping(DependencyDescriptor dep) {
        StringBuilder confs = new StringBuilder();
        String[] modConfs = dep.getModuleConfigurations();

        Map<String, List<String>> configMappings;
        if (dep instanceof DefaultDependencyDescriptor) {
            // The `getDependencyConfigurations()` implementation for DefaultDependencyDescriptor does some interpretation of the RHS of the configuration
            // mappings, and gets it wrong for mappings such as '*->@' pr '*->#'. So, instead, reach into the descriptor and get the raw mappings out.
            try {
                configMappings = (Map<String, List<String>>) dependencyConfigField.get(dep);
            } catch (IllegalAccessException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        } else {
            configMappings = new HashMap<String, List<String>>();
            for (String modConf : modConfs) {
                configMappings.put(modConf, Arrays.asList(dep.getDependencyConfigurations(modConfs)));
            }
        }

        for (int j = 0; j < modConfs.length; j++) {
            List<String> depConfs = configMappings.get(modConfs[j]);
            confs.append(modConfs[j]).append("->");
            for (int k = 0; k < depConfs.size(); k++) {
                confs.append(depConfs.get(k));
                if (k + 1 < depConfs.size()) {
                    confs.append(",");
                }
            }
            if (j + 1 < modConfs.length) {
                confs.append(";");
            }
        }
        return confs.toString();
    }

    private static void printAllExcludes(ModuleDescriptor md, SimpleXmlWriter writer) throws IOException {
        ExcludeRule[] excludes = md.getAllExcludeRules();
        for (ExcludeRule exclude : excludes) {
            writer.startElement("exclude");
            writer.attribute("org", exclude.getId().getModuleId().getOrganisation());
            writer.attribute("module", exclude.getId().getModuleId().getName());
            writer.attribute("artifact", exclude.getId().getName());
            writer.attribute("type", exclude.getId().getType());
            writer.attribute("ext", exclude.getId().getExt());
            String[] ruleConfs = exclude.getConfigurations();
            if (!Arrays.asList(ruleConfs).equals(
                    Arrays.asList(md.getConfigurationsNames()))) {
                writer.attribute("conf", Joiner.on(',').join(ruleConfs));
            }
            writer.attribute("matcher", exclude.getMatcher().getName());
            writer.endElement();
        }
    }

    private static void printDependencyExcludeRules(ModuleDescriptor md, SimpleXmlWriter writer,
                                                    ExcludeRule[] excludes) throws IOException {
        for (ExcludeRule exclude : excludes) {
            writer.startElement("exclude");
            writer.attribute("org", exclude.getId().getModuleId().getOrganisation());
            writer.attribute("module", exclude.getId().getModuleId().getName());
            writer.attribute("name", exclude.getId().getName());
            writer.attribute("type", exclude.getId().getType());
            writer.attribute("ext", exclude.getId().getExt());
            String[] ruleConfs = exclude.getConfigurations();
            if (!Arrays.asList(ruleConfs).equals(
                    Arrays.asList(md.getConfigurationsNames()))) {
                writer.attribute("conf", Joiner.on(',').join(ruleConfs));
            }
            writer.attribute("matcher", exclude.getMatcher().getName());
            writer.endElement();
        }
    }

    private static void printDependencyIncludeRules(ModuleDescriptor md, SimpleXmlWriter writer,
                                                    IncludeRule[] includes) throws IOException {
        for (IncludeRule include : includes) {
            writer.startElement("include");
            writer.attribute("name", include.getId().getName());
            writer.attribute("type", include.getId().getType());
            writer.attribute("ext", include.getId().getExt());
            String[] ruleConfs = include.getConfigurations();
            if (!Arrays.asList(ruleConfs).equals(
                    Arrays.asList(md.getConfigurationsNames()))) {
                writer.attribute("conf", Joiner.on(',').join(ruleConfs));
            }
            writer.attribute("matcher", include.getMatcher().getName());
            writer.endElement();
        }
    }

    private static void printDependencyArtefacts(ModuleDescriptor md, SimpleXmlWriter writer,
                                                 DependencyArtifactDescriptor[] depArtifacts) throws IOException {
        for (DependencyArtifactDescriptor depArtifact : depArtifacts) {
            writer.startElement("artifact");
            writer.attribute("name", depArtifact.getName());
            writer.attribute("type", depArtifact.getType());
            writer.attribute("ext", depArtifact.getExt());
            String[] dadconfs = depArtifact.getConfigurations();
            if (!Arrays.asList(dadconfs).equals(
                    Arrays.asList(md.getConfigurationsNames()))) {
                writer.attribute("conf", Joiner.on(',').join(dadconfs));
            }
            printExtraAttributes(depArtifact, writer);
            writer.endElement();
        }
    }

    /**
     * Writes the extra attributes of the given {@link org.apache.ivy.util.extendable.ExtendableItem} to the given <tt>PrintWriter</tt>.
     *
     * @param item the {@link org.apache.ivy.util.extendable.ExtendableItem}, cannot be <tt>null</tt>
     * @param writer the writer to use
     */
    private static void printExtraAttributes(ExtendableItem item, SimpleXmlWriter writer) throws IOException {
        printExtraAttributes(item.getQualifiedExtraAttributes(), writer);
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

    private static void printPublications(ModuleDescriptor md, SimpleXmlWriter writer) throws IOException {
        writer.startElement("publications");
        Artifact[] artifacts = md.getAllArtifacts();
        for (int i = 0; i < artifacts.length; i++) {
            Artifact artifact = artifacts[i];
            writer.startElement("artifact");
            writer.attribute("name", artifact.getName());
            writer.attribute("type", artifact.getType());
            writer.attribute("ext", artifact.getExt());
            writer.attribute("conf", getConfs(artifact));
            printExtraAttributes(artifact, writer);
            writer.endElement();
        }
        writer.endElement();
    }

    private static void printConfigurations(ModuleDescriptor md, SimpleXmlWriter writer) throws IOException {
        Configuration[] confs = md.getConfigurations();
        if (confs.length > 0) {
            writer.startElement("configurations");
            for (Configuration conf : confs) {
                printConfiguration(conf, writer);
            }
            writer.endElement();
        }
    }

    private static void printConfiguration(Configuration conf, SimpleXmlWriter writer) throws IOException {
        writer.startElement("conf");
        writer.attribute("name", conf.getName());
        writer.attribute("visibility", conf.getVisibility().toString());
        String description = conf.getDescription();
        if (description != null) {
            writer.attribute("description", description);
        }
        String[] exts = conf.getExtends();
        if (exts.length > 0) {
            writer.attribute("extends", Joiner.on(',').join(exts));
        }
        if (!conf.isTransitive()) {
            writer.attribute("transitive", "false");
        }
        if (conf.getDeprecated() != null) {
            writer.attribute("deprecated", conf.getDeprecated());
        }
        printExtraAttributes(conf, writer);
        writer.endElement();
    }

    private static void printInfoTag(ModuleDescriptor md, SimpleXmlWriter writer) throws IOException {
        ModuleRevisionId moduleRevisionId = md.getModuleRevisionId();
        writer.startElement("info");

        writer.attribute("organisation", moduleRevisionId.getOrganisation());
        writer.attribute("module", moduleRevisionId.getName());

        ModuleRevisionId resolvedModuleRevisionId = md.getResolvedModuleRevisionId();
        String branch = resolvedModuleRevisionId.getBranch();
        if (branch != null) {
            writer.attribute("branch", branch);
        }
        String revision = resolvedModuleRevisionId.getRevision();
        if (revision != null) {
            writer.attribute("revision", revision);
        }
        writer.attribute("status", md.getStatus());

        SimpleDateFormat ivyDateFormat = new SimpleDateFormat(IVY_DATE_PATTERN);
        Date publicationDate = md.getResolvedPublicationDate();
        if (publicationDate != null) {
            writer.attribute("publication", ivyDateFormat.format(publicationDate));
        }
        if (md.isDefault()) {
            writer.attribute("default", "true");
        }
        if (md instanceof DefaultModuleDescriptor) {
            DefaultModuleDescriptor dmd = (DefaultModuleDescriptor) md;
            if (dmd.getNamespace() != null && !dmd.getNamespace().getName().equals("system")) {
                writer.attribute("namespace", dmd.getNamespace().getName());
            }
        }
        if (!md.getExtraAttributes().isEmpty()) {
            printExtraAttributes(md, writer);
        }

        ExtendsDescriptor[] parents = md.getInheritedDescriptors();
        if (parents.length != 0) {
            throw new UnsupportedOperationException("Extends descriptors not supported.");
        }

        License[] licenses = md.getLicenses();
        for (int i = 0; i < licenses.length; i++) {
            License license = licenses[i];
            writer.startElement("license");
            if (license.getName() != null) {
                writer.attribute("name", license.getName());
            }
            if (license.getUrl() != null) {
                writer.attribute("url", license.getUrl());
            }
            writer.endElement();
        }

        if (md.getHomePage() != null || md.getDescription() != null) {
            writer.startElement("description");
            if (md.getHomePage() != null) {
                writer.attribute("homepage", md.getHomePage());
            }
            if (md.getDescription() != null && md.getDescription().trim().length() > 0) {
                writer.characters(md.getDescription());
            }
            writer.endElement();
        }

        for (Iterator it = md.getExtraInfo().entrySet().iterator(); it.hasNext();) {
            Map.Entry extraDescr = (Map.Entry) it.next();
            if (extraDescr.getValue() == null || ((String) extraDescr.getValue()).length() == 0) {
                continue;
            }
            if (extraDescr.getKey() instanceof NamespaceId) {
                NamespaceId id = (NamespaceId) extraDescr.getKey();
                writer.startElement(String.format("ns:%s", id.getName()));
                writer.attribute("xmlns:ns", id.getNamespace());
            } else {
                writer.startElement(extraDescr.getKey().toString());
            }
            writer.characters(extraDescr.getValue().toString());
            writer.endElement();
        }

        writer.endElement();
    }

    private static String getConfs(Artifact artifact) {
        return Joiner.on(",").join(artifact.getConfigurations());
    }
}
