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

import org.apache.ivy.Ivy;
import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.matcher.MapMatcher;
import org.apache.ivy.util.Message;
import org.apache.ivy.util.StringUtils;
import org.apache.ivy.util.XMLHelper;
import org.apache.ivy.util.extendable.ExtendableItem;
import org.gradle.api.UncheckedIOException;
import org.gradle.util.TextUtil;

import java.io.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

public class IvyModuleDescriptorWriter {
    private IvyModuleDescriptorWriter() {
        //Utility class
    }

    private static void write(ModuleDescriptor md, Writer writer) throws IOException {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        writer.write(TextUtil.getPlatformLineSeparator());
        StringBuffer xmlNamespace = new StringBuffer();
        Map namespaces = md.getExtraAttributesNamespaces();
        for (Iterator iter = namespaces.entrySet().iterator(); iter.hasNext();) {
            Map.Entry ns = (Map.Entry) iter.next();
            xmlNamespace.append(" xmlns:").append(ns.getKey()).append("=\"")
                    .append(ns.getValue()).append("\"");
        }

        String version = "2.0";
        if (md.getInheritedDescriptors().length > 0) {
            version = "2.2";
        }

        writer.write("<ivy-module version=\"" + version + "\"" + xmlNamespace + ">");
        writer.write(TextUtil.getPlatformLineSeparator());
        printInfoTag(md, writer);
        printConfigurations(md, writer);
        printPublications(md, writer);
        printDependencies(md, writer);
        writer.write("</ivy-module>");
        writer.write(TextUtil.getPlatformLineSeparator());
    }

    public static void write(ModuleDescriptor md, File output) {
        if (output.getParentFile() != null) {
            output.getParentFile().mkdirs();
        }
        try {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
            try {
                write(md, writer);
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not write Ivy descriptor to '%s'.", output), e);
        }
    }

    private static void printDependencies(ModuleDescriptor md, Writer writer) throws IOException {
        DependencyDescriptor[] dds = md.getDependencies();
        if (dds.length > 0) {
            writer.write("\t<dependencies>");
            writer.write(TextUtil.getPlatformLineSeparator());
            for (int i = 0; i < dds.length; i++) {
                DependencyDescriptor dep = dds[i];
                writer.write("\t\t");
                printDependency(md, dep, writer);
            }
            printAllExcludes(md, writer);
            printAllMediators(md, writer);
            writer.write("\t</dependencies>");
            writer.write(TextUtil.getPlatformLineSeparator());
        }
    }

    protected static void printDependency(ModuleDescriptor md, DependencyDescriptor dep,
                                          Writer writer) throws IOException {
        writer.write("<dependency");
        ModuleRevisionId dependencyRevisionId = dep.getDependencyRevisionId();
        writer.write(" org=\""
                + XMLHelper.escape(dependencyRevisionId.getOrganisation()) + "\"");
        writer.write(" name=\""
                + XMLHelper.escape(dependencyRevisionId.getName()) + "\"");
        if (dependencyRevisionId.getBranch() != null) {
            writer.write(" branch=\""
                    + XMLHelper.escape(dependencyRevisionId.getBranch()) + "\"");
        }
        writer.write(" rev=\""
                + XMLHelper.escape(dependencyRevisionId.getRevision()) + "\"");
        if (!dep.getDynamicConstraintDependencyRevisionId()
                .equals(dependencyRevisionId)) {
            if (dep.getDynamicConstraintDependencyRevisionId().getBranch() != null) {
                writer.write(" branchConstraint=\"" + XMLHelper.escape(
                        dep.getDynamicConstraintDependencyRevisionId().getBranch()) + "\"");
            }
            writer.write(" revConstraint=\"" + XMLHelper.escape(
                    dep.getDynamicConstraintDependencyRevisionId().getRevision()) + "\"");
        }
        if (dep.isForce()) {
            writer.write(" force=\"" + dep.isForce() + "\"");
        }
        if (dep.isChanging()) {
            writer.write(" changing=\"" + dep.isChanging() + "\"");
        }
        if (!dep.isTransitive()) {
            writer.write(" transitive=\"" + dep.isTransitive() + "\"");
        }
        writer.write(" conf=\"");
        String[] modConfs = dep.getModuleConfigurations();
        for (int j = 0; j < modConfs.length; j++) {
            String[] depConfs = dep.getDependencyConfigurations(modConfs[j]);
            writer.write(XMLHelper.escape(modConfs[j]) + "->");
            for (int k = 0; k < depConfs.length; k++) {
                writer.write(XMLHelper.escape(depConfs[k]));
                if (k + 1 < depConfs.length) {
                    writer.write(",");
                }
            }
            if (j + 1 < modConfs.length) {
                writer.write(";");
            }
        }
        writer.write("\"");

        printExtraAttributes(dep, writer, " ");

        DependencyArtifactDescriptor[] depArtifacts = dep.getAllDependencyArtifacts();
        if (depArtifacts.length > 0) {
            writer.write(">");
            writer.write(TextUtil.getPlatformLineSeparator());
        }
        printDependencyArtefacts(md, writer, depArtifacts);

        IncludeRule[] includes = dep.getAllIncludeRules();
        if (includes.length > 0 && depArtifacts.length == 0) {
            writer.write(">");
            writer.write(TextUtil.getPlatformLineSeparator());
        }
        printDependencyIncludeRules(md, writer, includes);

        ExcludeRule[] excludes = dep.getAllExcludeRules();
        if (excludes.length > 0 && includes.length == 0 && depArtifacts.length == 0) {
            writer.write(">");
            writer.write(TextUtil.getPlatformLineSeparator());
        }
        printDependencyExcludeRules(md, writer, excludes);
        if (includes.length + excludes.length + depArtifacts.length == 0) {
            writer.write("/>");
            writer.write(TextUtil.getPlatformLineSeparator());
        } else {
            writer.write("\t\t</dependency>");
            writer.write(TextUtil.getPlatformLineSeparator());
        }
    }

    private static void printAllMediators(ModuleDescriptor md, Writer writer) throws IOException {
        Map/*<MapMatcher, DependencyDescriptorMediator>*/ mediators
                = md.getAllDependencyDescriptorMediators().getAllRules();

        for (Iterator iterator = mediators.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry mediatorRule = (Map.Entry) iterator.next();
            MapMatcher matcher = (MapMatcher) mediatorRule.getKey();
            DependencyDescriptorMediator mediator =
                    (DependencyDescriptorMediator) mediatorRule.getValue();

            if (mediator instanceof OverrideDependencyDescriptorMediator) {
                OverrideDependencyDescriptorMediator oddm =
                        (OverrideDependencyDescriptorMediator) mediator;

                writer.write("\t\t<override");
                writer.write(" org=\"" + XMLHelper.escape(
                        (String) matcher.getAttributes().get(IvyPatternHelper.ORGANISATION_KEY))
                        + "\"");
                writer.write(" module=\"" + XMLHelper.escape(
                        (String) matcher.getAttributes().get(IvyPatternHelper.MODULE_KEY))
                        + "\"");
                writer.write(" matcher=\"" + XMLHelper.escape(
                        matcher.getPatternMatcher().getName())
                        + "\"");
                if (oddm.getBranch() != null) {
                    writer.write(" branch=\"" + XMLHelper.escape(oddm.getBranch()) + "\"");
                }
                if (oddm.getVersion() != null) {
                    writer.write(" rev=\"" + XMLHelper.escape(oddm.getVersion()) + "\"");
                }
                writer.write("/>");
                writer.write(TextUtil.getPlatformLineSeparator());
            } else {
                Message.verbose("ignoring unhandled DependencyDescriptorMediator: "
                        + mediator.getClass());
            }
        }
    }

    private static void printAllExcludes(ModuleDescriptor md, Writer writer) throws IOException {
        ExcludeRule[] excludes = md.getAllExcludeRules();
        if (excludes.length > 0) {
            for (int j = 0; j < excludes.length; j++) {
                writer.write("\t\t<exclude");
                writer.write(" org=\""
                        + XMLHelper.escape(excludes[j].getId().getModuleId().getOrganisation())
                        + "\"");
                writer.write(" module=\""
                        + XMLHelper.escape(excludes[j].getId().getModuleId().getName())
                        + "\"");
                writer.write(" artifact=\"" + XMLHelper.escape(excludes[j].getId().getName()) + "\"");
                writer.write(" type=\"" + XMLHelper.escape(excludes[j].getId().getType()) + "\"");
                writer.write(" ext=\"" + XMLHelper.escape(excludes[j].getId().getExt()) + "\"");
                String[] ruleConfs = excludes[j].getConfigurations();
                if (!Arrays.asList(ruleConfs).equals(
                        Arrays.asList(md.getConfigurationsNames()))) {
                    writer.write(" conf=\"");
                    for (int k = 0; k < ruleConfs.length; k++) {
                        writer.write(XMLHelper.escape(ruleConfs[k]));
                        if (k + 1 < ruleConfs.length) {
                            writer.write(",");
                        }
                    }
                    writer.write("\"");
                }
                writer.write(" matcher=\""
                        + XMLHelper.escape(excludes[j].getMatcher().getName()) + "\"");
                writer.write("/>");
                writer.write(TextUtil.getPlatformLineSeparator());
            }
        }
    }

    private static void printDependencyExcludeRules(ModuleDescriptor md, Writer writer,
                                                    ExcludeRule[] excludes) throws IOException {
        if (excludes.length > 0) {
            for (int j = 0; j < excludes.length; j++) {
                writer.write("\t\t\t<exclude");
                writer.write(" org=\""
                        + XMLHelper.escape(excludes[j].getId().getModuleId().getOrganisation())
                        + "\"");
                writer.write(" module=\""
                        + XMLHelper.escape(excludes[j].getId().getModuleId().getName())
                        + "\"");
                writer.write(" name=\"" + XMLHelper.escape(excludes[j].getId().getName()) + "\"");
                writer.write(" type=\"" + XMLHelper.escape(excludes[j].getId().getType()) + "\"");
                writer.write(" ext=\"" + XMLHelper.escape(excludes[j].getId().getExt()) + "\"");
                String[] ruleConfs = excludes[j].getConfigurations();
                if (!Arrays.asList(ruleConfs).equals(
                        Arrays.asList(md.getConfigurationsNames()))) {
                    writer.write(" conf=\"");
                    for (int k = 0; k < ruleConfs.length; k++) {
                        writer.write(XMLHelper.escape(ruleConfs[k]));
                        if (k + 1 < ruleConfs.length) {
                            writer.write(",");
                        }
                    }
                    writer.write("\"");
                }
                writer.write(" matcher=\""
                        + XMLHelper.escape(excludes[j].getMatcher().getName()) + "\"");
                writer.write("/>");
                writer.write(TextUtil.getPlatformLineSeparator());
            }
        }
    }

    private static void printDependencyIncludeRules(ModuleDescriptor md, Writer writer,
                                                    IncludeRule[] includes) throws IOException {
        if (includes.length > 0) {
            for (int j = 0; j < includes.length; j++) {
                writer.write("\t\t\t<include");
                writer.write(" name=\"" + XMLHelper.escape(includes[j].getId().getName()) + "\"");
                writer.write(" type=\"" + XMLHelper.escape(includes[j].getId().getType()) + "\"");
                writer.write(" ext=\"" + XMLHelper.escape(includes[j].getId().getExt()) + "\"");
                String[] ruleConfs = includes[j].getConfigurations();
                if (!Arrays.asList(ruleConfs).equals(
                        Arrays.asList(md.getConfigurationsNames()))) {
                    writer.write(" conf=\"");
                    for (int k = 0; k < ruleConfs.length; k++) {
                        writer.write(XMLHelper.escape(ruleConfs[k]));
                        if (k + 1 < ruleConfs.length) {
                            writer.write(",");
                        }
                    }
                    writer.write("\"");
                }
                writer.write(" matcher=\""
                        + XMLHelper.escape(includes[j].getMatcher().getName()) + "\"");
                writer.write("/>");
                writer.write(TextUtil.getPlatformLineSeparator());
            }
        }
    }

    private static void printDependencyArtefacts(ModuleDescriptor md, Writer writer,
                                                 DependencyArtifactDescriptor[] depArtifacts) throws IOException {
        if (depArtifacts.length > 0) {
            for (int j = 0; j < depArtifacts.length; j++) {
                writer.write("\t\t\t<artifact");
                writer.write(" name=\"" + XMLHelper.escape(depArtifacts[j].getName()) + "\"");
                writer.write(" type=\"" + XMLHelper.escape(depArtifacts[j].getType()) + "\"");
                writer.write(" ext=\"" + XMLHelper.escape(depArtifacts[j].getExt()) + "\"");
                String[] dadconfs = depArtifacts[j].getConfigurations();
                if (!Arrays.asList(dadconfs).equals(
                        Arrays.asList(md.getConfigurationsNames()))) {
                    writer.write(" conf=\"");
                    for (int k = 0; k < dadconfs.length; k++) {
                        writer.write(XMLHelper.escape(dadconfs[k]));
                        if (k + 1 < dadconfs.length) {
                            writer.write(",");
                        }
                    }
                    writer.write("\"");
                }
                printExtraAttributes(depArtifacts[j], writer, " ");
                writer.write("/>");
                writer.write(TextUtil.getPlatformLineSeparator());
            }
        }
    }

    /**
     * Writes the extra attributes of the given {@link org.apache.ivy.util.extendable.ExtendableItem} to the given <tt>PrintWriter</tt>.
     *
     * @param item the {@link org.apache.ivy.util.extendable.ExtendableItem}, cannot be <tt>null</tt>
     * @param writer the writer to use
     * @param prefix the string to write before writing the attributes (if any)
     */
    private static void printExtraAttributes(ExtendableItem item, Writer writer, String prefix) throws IOException {
        printExtraAttributes(item.getQualifiedExtraAttributes(), writer, prefix);
    }

    /**
     * Writes the specified <tt>Map</tt> containing the extra attributes to the given <tt>PrintWriter</tt>.
     *
     * @param extra the extra attributes, can be <tt>null</tt>
     * @param writer the writer to use
     * @param prefix the string to write before writing the attributes (if any)
     */
    private static void printExtraAttributes(Map extra, Writer writer, String prefix) throws IOException {
        if (extra == null) {
            return;
        }

        String delim = prefix;
        for (Iterator iter = extra.entrySet().iterator(); iter.hasNext();) {
            Map.Entry entry = (Map.Entry) iter.next();
            writer.write(delim + entry.getKey() + "=\""
                    + XMLHelper.escape(entry.getValue().toString()) + "\"");
            delim = " ";
        }
    }

    private static void printPublications(ModuleDescriptor md, Writer writer) throws IOException {
        writer.write("\t<publications>");
        writer.write(TextUtil.getPlatformLineSeparator());
        Artifact[] artifacts = md.getAllArtifacts();
        for (int i = 0; i < artifacts.length; i++) {
            writer.write("\t\t<artifact");
            writer.write(" name=\"" + XMLHelper.escape(artifacts[i].getName()) + "\"");
            writer.write(" type=\"" + XMLHelper.escape(artifacts[i].getType()) + "\"");
            writer.write(" ext=\"" + XMLHelper.escape(artifacts[i].getExt()) + "\"");
            writer.write(" conf=\"" + XMLHelper.escape(getConfs(md, artifacts[i])) + "\"");
            printExtraAttributes(artifacts[i], writer, " ");
            writer.write("/>");
            writer.write(TextUtil.getPlatformLineSeparator());
        }
        writer.write("\t</publications>");
        writer.write(TextUtil.getPlatformLineSeparator());
    }

    private static void printConfigurations(ModuleDescriptor md, Writer writer) throws IOException {
        Configuration[] confs = md.getConfigurations();
        if (confs.length > 0) {
            writer.write("\t<configurations>");
            writer.write(TextUtil.getPlatformLineSeparator());
            for (int i = 0; i < confs.length; i++) {
                Configuration conf = confs[i];
                writer.write("\t\t");
                printConfiguration(conf, writer);
            }
            writer.write("\t</configurations>");
            writer.write(TextUtil.getPlatformLineSeparator());
        }
    }

    protected static void printConfiguration(Configuration conf, Writer writer) throws IOException {
        writer.write("<conf");
        writer.write(" name=\"" + XMLHelper.escape(conf.getName()) + "\"");
        writer.write(" visibility=\""
                + XMLHelper.escape(conf.getVisibility().toString()) + "\"");
        String description = conf.getDescription();
        if (description != null) {
            writer.write(" description=\""
                    + XMLHelper.escape(description) + "\"");
        }
        String[] exts = conf.getExtends();
        if (exts.length > 0) {
            writer.write(" extends=\"");
            for (int j = 0; j < exts.length; j++) {
                writer.write(XMLHelper.escape(exts[j]));
                if (j + 1 < exts.length) {
                    writer.write(",");
                }
            }
            writer.write("\"");
        }
        if (!conf.isTransitive()) {
            writer.write(" transitive=\"false\"");
            writer.write(TextUtil.getPlatformLineSeparator());
        }
        if (conf.getDeprecated() != null) {
            writer.write(" deprecated=\"" + XMLHelper.escape(conf.getDeprecated()) + "\"");
        }
        printExtraAttributes(conf, writer, " ");
        writer.write("/>");
        writer.write(TextUtil.getPlatformLineSeparator());
    }

    private static void printInfoTag(ModuleDescriptor md, Writer writer) throws IOException {
        ModuleRevisionId moduleRevisionId = md.getModuleRevisionId();
        writer.write("\t<info organisation=\""
                + XMLHelper.escape(moduleRevisionId.getOrganisation())
                + "\"");
        writer.write(TextUtil.getPlatformLineSeparator());
        writer.write("\t\tmodule=\"" + XMLHelper.escape(moduleRevisionId.getName()) + "\"");
        writer.write(TextUtil.getPlatformLineSeparator());

        ModuleRevisionId resolvedModuleRevisionId = md.getResolvedModuleRevisionId();
        String branch = resolvedModuleRevisionId.getBranch();
        if (branch != null) {
            writer.write("\t\tbranch=\"" + XMLHelper.escape(branch) + "\"");
            writer.write(TextUtil.getPlatformLineSeparator());
        }
        String revision = resolvedModuleRevisionId.getRevision();
        if (revision != null) {
            writer.write("\t\trevision=\"" + XMLHelper.escape(revision) + "\"");
            writer.write(TextUtil.getPlatformLineSeparator());
        }
        writer.write("\t\tstatus=\"" + XMLHelper.escape(md.getStatus()) + "\"");
        writer.write(TextUtil.getPlatformLineSeparator());
        writer.write("\t\tpublication=\""
                + Ivy.DATE_FORMAT.format(md.getResolvedPublicationDate()) + "\"");
        writer.write(TextUtil.getPlatformLineSeparator());
        if (md.isDefault()) {
            writer.write("\t\tdefault=\"true\"");
            writer.write(TextUtil.getPlatformLineSeparator());
        }
        if (md instanceof DefaultModuleDescriptor) {
            DefaultModuleDescriptor dmd = (DefaultModuleDescriptor) md;
            if (dmd.getNamespace() != null && !dmd.getNamespace().getName().equals("system")) {
                writer.write("\t\tnamespace=\""
                        + XMLHelper.escape(dmd.getNamespace().getName()) + "\"");
                writer.write(TextUtil.getPlatformLineSeparator());
            }
        }
        if (!md.getExtraAttributes().isEmpty()) {
            printExtraAttributes(md, writer, "\t\t");
            writer.write(TextUtil.getPlatformLineSeparator());
        }
        if (requireInnerInfoElement(md)) {
            writer.write("\t>");
            writer.write(TextUtil.getPlatformLineSeparator());
            ExtendsDescriptor[] parents = md.getInheritedDescriptors();
            for (int i = 0; i < parents.length; i++) {
                ExtendsDescriptor parent = parents[i];
                ModuleRevisionId mrid = parent.getParentRevisionId();
                writer.write("\t\t<extends organisation=\"" + XMLHelper.escape(mrid.getOrganisation()) + "\""
                        + " module=\"" + XMLHelper.escape(mrid.getName()) + "\""
                        + " revision=\"" + XMLHelper.escape(mrid.getRevision()) + "\"");

                String location = parent.getLocation();
                if (location != null) {
                    writer.write(" location=\"" + XMLHelper.escape(location) + "\"");
                }
                writer.write(" extendType=\"" + StringUtils.join(parent.getExtendsTypes(), ",") + "\"");
                writer.write("/>");
                writer.write(TextUtil.getPlatformLineSeparator());
            }
            License[] licenses = md.getLicenses();
            for (int i = 0; i < licenses.length; i++) {
                License license = licenses[i];
                writer.write("\t\t<license ");
                if (license.getName() != null) {
                    writer.write("name=\"" + XMLHelper.escape(license.getName()) + "\" ");
                }
                if (license.getUrl() != null) {
                    writer.write("url=\"" + XMLHelper.escape(license.getUrl()) + "\" ");
                }
                writer.write("/>");
                writer.write(TextUtil.getPlatformLineSeparator());
            }
            if (md.getHomePage() != null || md.getDescription() != null) {
                writer.write("\t\t<description");
                if (md.getHomePage() != null) {
                    writer.write(" homepage=\"" + XMLHelper.escape(md.getHomePage()) + "\"");
                }
                if (md.getDescription() != null && md.getDescription().trim().length() > 0) {
                    writer.write(">");
                    writer.write(TextUtil.getPlatformLineSeparator());
                    writer.write("\t\t" + XMLHelper.escape(md.getDescription()));
                    writer.write(TextUtil.getPlatformLineSeparator());
                    writer.write("\t\t</description>");
                    writer.write(TextUtil.getPlatformLineSeparator());
                } else {
                    writer.write(" />");
                    writer.write(TextUtil.getPlatformLineSeparator());
                }
            }
            for (Iterator it = md.getExtraInfo().entrySet().iterator(); it.hasNext();) {
                Map.Entry extraDescr = (Map.Entry) it.next();
                if (extraDescr.getValue() == null
                        || ((String) extraDescr.getValue()).length() == 0) {
                    continue;
                }
                writer.write("\t\t<");
                writer.write(extraDescr.getKey().toString());
                writer.write(">");
                writer.write(XMLHelper.escape((String) extraDescr.getValue()));
                writer.write("</");
                writer.write(extraDescr.getKey().toString());
                writer.write(">");
                writer.write(TextUtil.getPlatformLineSeparator());
            }
            writer.write("\t</info>");
            writer.write(TextUtil.getPlatformLineSeparator());
        } else {
            writer.write("\t/>");
            writer.write(TextUtil.getPlatformLineSeparator());
        }
    }

    private static boolean requireInnerInfoElement(ModuleDescriptor md) {
        return md.getExtraInfo().size() > 0
                || md.getHomePage() != null
                || (md.getDescription() != null && md.getDescription().trim().length() > 0)
                || md.getLicenses().length > 0
                || md.getInheritedDescriptors().length > 0;
    }

    private static String getConfs(ModuleDescriptor md, Artifact artifact) {
        StringBuffer ret = new StringBuffer();

        String[] confs = md.getConfigurationsNames();
        for (int i = 0; i < confs.length; i++) {
            if (Arrays.asList(md.getArtifacts(confs[i])).contains(artifact)) {
                ret.append(confs[i]).append(",");
            }
        }
        if (ret.length() > 0) {
            ret.setLength(ret.length() - 1);
        }
        return ret.toString();
    }
}
