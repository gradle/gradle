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

import java.io.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

public class IvyModuleDescriptorWriter {
    private IvyModuleDescriptorWriter() {
        //Utility class
    }

    public static void write(ModuleDescriptor md, PrintWriter writer) throws IOException {
        writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
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

        writer.println("<ivy-module version=\"" + version + "\"" + xmlNamespace + ">");
        printInfoTag(md, writer);
        printConfigurations(md, writer);
        printPublications(md, writer);
        printDependencies(md, writer);
        writer.println("</ivy-module>");
        if (writer.checkError()) {
            throw new IOException("Unknown Error occured while writing Ivy module descriptor");
        }
    }

    public static void write(ModuleDescriptor md, File output)
            throws IOException {
        if (output.getParentFile() != null) {
            output.getParentFile().mkdirs();
        }
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
        try {
            write(md, writer);
        } finally {
            writer.close();
        }
    }

    private static void printDependencies(ModuleDescriptor md, PrintWriter out) {
        DependencyDescriptor[] dds = md.getDependencies();
        if (dds.length > 0) {
            out.println("\t<dependencies>");
            for (int i = 0; i < dds.length; i++) {
                DependencyDescriptor dep = dds[i];
                out.print("\t\t");
                printDependency(md, dep, out);
            }
            printAllExcludes(md, out);
            printAllMediators(md, out);
            out.println("\t</dependencies>");
        }
    }

    protected static void printDependency(ModuleDescriptor md, DependencyDescriptor dep,
                                          PrintWriter out) {
        out.print("<dependency");
        ModuleRevisionId dependencyRevisionId = dep.getDependencyRevisionId();
        out.print(" org=\""
                + XMLHelper.escape(dependencyRevisionId.getOrganisation()) + "\"");
        out.print(" name=\""
                + XMLHelper.escape(dependencyRevisionId.getName()) + "\"");
        if (dependencyRevisionId.getBranch() != null) {
            out.print(" branch=\""
                    + XMLHelper.escape(dependencyRevisionId.getBranch()) + "\"");
        }
        out.print(" rev=\""
                + XMLHelper.escape(dependencyRevisionId.getRevision()) + "\"");
        if (!dep.getDynamicConstraintDependencyRevisionId()
                .equals(dependencyRevisionId)) {
            if (dep.getDynamicConstraintDependencyRevisionId().getBranch() != null) {
                out.print(" branchConstraint=\"" + XMLHelper.escape(
                        dep.getDynamicConstraintDependencyRevisionId().getBranch()) + "\"");
            }
            out.print(" revConstraint=\"" + XMLHelper.escape(
                    dep.getDynamicConstraintDependencyRevisionId().getRevision()) + "\"");
        }
        if (dep.isForce()) {
            out.print(" force=\"" + dep.isForce() + "\"");
        }
        if (dep.isChanging()) {
            out.print(" changing=\"" + dep.isChanging() + "\"");
        }
        if (!dep.isTransitive()) {
            out.print(" transitive=\"" + dep.isTransitive() + "\"");
        }
        out.print(" conf=\"");
        String[] modConfs = dep.getModuleConfigurations();
        for (int j = 0; j < modConfs.length; j++) {
            String[] depConfs = dep.getDependencyConfigurations(modConfs[j]);
            out.print(XMLHelper.escape(modConfs[j]) + "->");
            for (int k = 0; k < depConfs.length; k++) {
                out.print(XMLHelper.escape(depConfs[k]));
                if (k + 1 < depConfs.length) {
                    out.print(",");
                }
            }
            if (j + 1 < modConfs.length) {
                out.print(";");
            }
        }
        out.print("\"");

        printExtraAttributes(dep, out, " ");

        DependencyArtifactDescriptor[] depArtifacts = dep.getAllDependencyArtifacts();
        if (depArtifacts.length > 0) {
            out.println(">");
        }
        printDependencyArtefacts(md, out, depArtifacts);

        IncludeRule[] includes = dep.getAllIncludeRules();
        if (includes.length > 0 && depArtifacts.length == 0) {
            out.println(">");
        }
        printDependencyIncludeRules(md, out, includes);

        ExcludeRule[] excludes = dep.getAllExcludeRules();
        if (excludes.length > 0 && includes.length == 0 && depArtifacts.length == 0) {
            out.println(">");
        }
        printDependencyExcludeRules(md, out, excludes);
        if (includes.length + excludes.length + depArtifacts.length == 0) {
            out.println("/>");
        } else {
            out.println("\t\t</dependency>");
        }
    }

    private static void printAllMediators(ModuleDescriptor md, PrintWriter out) {
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

                out.print("\t\t<override");
                out.print(" org=\"" + XMLHelper.escape(
                        (String) matcher.getAttributes().get(IvyPatternHelper.ORGANISATION_KEY))
                        + "\"");
                out.print(" module=\"" + XMLHelper.escape(
                        (String) matcher.getAttributes().get(IvyPatternHelper.MODULE_KEY))
                        + "\"");
                out.print(" matcher=\"" + XMLHelper.escape(
                        matcher.getPatternMatcher().getName())
                        + "\"");
                if (oddm.getBranch() != null) {
                    out.print(" branch=\"" + XMLHelper.escape(oddm.getBranch()) + "\"");
                }
                if (oddm.getVersion() != null) {
                    out.print(" rev=\"" + XMLHelper.escape(oddm.getVersion()) + "\"");
                }
                out.println("/>");
            } else {
                Message.verbose("ignoring unhandled DependencyDescriptorMediator: "
                        + mediator.getClass());
            }
        }
    }

    private static void printAllExcludes(ModuleDescriptor md, PrintWriter out) {
        ExcludeRule[] excludes = md.getAllExcludeRules();
        if (excludes.length > 0) {
            for (int j = 0; j < excludes.length; j++) {
                out.print("\t\t<exclude");
                out.print(" org=\""
                        + XMLHelper.escape(excludes[j].getId().getModuleId().getOrganisation())
                        + "\"");
                out.print(" module=\""
                        + XMLHelper.escape(excludes[j].getId().getModuleId().getName())
                        + "\"");
                out.print(" artifact=\"" + XMLHelper.escape(excludes[j].getId().getName()) + "\"");
                out.print(" type=\"" + XMLHelper.escape(excludes[j].getId().getType()) + "\"");
                out.print(" ext=\"" + XMLHelper.escape(excludes[j].getId().getExt()) + "\"");
                String[] ruleConfs = excludes[j].getConfigurations();
                if (!Arrays.asList(ruleConfs).equals(
                        Arrays.asList(md.getConfigurationsNames()))) {
                    out.print(" conf=\"");
                    for (int k = 0; k < ruleConfs.length; k++) {
                        out.print(XMLHelper.escape(ruleConfs[k]));
                        if (k + 1 < ruleConfs.length) {
                            out.print(",");
                        }
                    }
                    out.print("\"");
                }
                out.print(" matcher=\""
                        + XMLHelper.escape(excludes[j].getMatcher().getName()) + "\"");
                out.println("/>");
            }
        }
    }

    private static void printDependencyExcludeRules(ModuleDescriptor md, PrintWriter out,
                                                    ExcludeRule[] excludes) {
        if (excludes.length > 0) {
            for (int j = 0; j < excludes.length; j++) {
                out.print("\t\t\t<exclude");
                out.print(" org=\""
                        + XMLHelper.escape(excludes[j].getId().getModuleId().getOrganisation())
                        + "\"");
                out.print(" module=\""
                        + XMLHelper.escape(excludes[j].getId().getModuleId().getName())
                        + "\"");
                out.print(" name=\"" + XMLHelper.escape(excludes[j].getId().getName()) + "\"");
                out.print(" type=\"" + XMLHelper.escape(excludes[j].getId().getType()) + "\"");
                out.print(" ext=\"" + XMLHelper.escape(excludes[j].getId().getExt()) + "\"");
                String[] ruleConfs = excludes[j].getConfigurations();
                if (!Arrays.asList(ruleConfs).equals(
                        Arrays.asList(md.getConfigurationsNames()))) {
                    out.print(" conf=\"");
                    for (int k = 0; k < ruleConfs.length; k++) {
                        out.print(XMLHelper.escape(ruleConfs[k]));
                        if (k + 1 < ruleConfs.length) {
                            out.print(",");
                        }
                    }
                    out.print("\"");
                }
                out.print(" matcher=\""
                        + XMLHelper.escape(excludes[j].getMatcher().getName()) + "\"");
                out.println("/>");
            }
        }
    }

    private static void printDependencyIncludeRules(ModuleDescriptor md, PrintWriter out,
                                                    IncludeRule[] includes) {
        if (includes.length > 0) {
            for (int j = 0; j < includes.length; j++) {
                out.print("\t\t\t<include");
                out.print(" name=\"" + XMLHelper.escape(includes[j].getId().getName()) + "\"");
                out.print(" type=\"" + XMLHelper.escape(includes[j].getId().getType()) + "\"");
                out.print(" ext=\"" + XMLHelper.escape(includes[j].getId().getExt()) + "\"");
                String[] ruleConfs = includes[j].getConfigurations();
                if (!Arrays.asList(ruleConfs).equals(
                        Arrays.asList(md.getConfigurationsNames()))) {
                    out.print(" conf=\"");
                    for (int k = 0; k < ruleConfs.length; k++) {
                        out.print(XMLHelper.escape(ruleConfs[k]));
                        if (k + 1 < ruleConfs.length) {
                            out.print(",");
                        }
                    }
                    out.print("\"");
                }
                out.print(" matcher=\""
                        + XMLHelper.escape(includes[j].getMatcher().getName()) + "\"");
                out.println("/>");
            }
        }
    }

    private static void printDependencyArtefacts(ModuleDescriptor md, PrintWriter out,
                                                 DependencyArtifactDescriptor[] depArtifacts) {
        if (depArtifacts.length > 0) {
            for (int j = 0; j < depArtifacts.length; j++) {
                out.print("\t\t\t<artifact");
                out.print(" name=\"" + XMLHelper.escape(depArtifacts[j].getName()) + "\"");
                out.print(" type=\"" + XMLHelper.escape(depArtifacts[j].getType()) + "\"");
                out.print(" ext=\"" + XMLHelper.escape(depArtifacts[j].getExt()) + "\"");
                String[] dadconfs = depArtifacts[j].getConfigurations();
                if (!Arrays.asList(dadconfs).equals(
                        Arrays.asList(md.getConfigurationsNames()))) {
                    out.print(" conf=\"");
                    for (int k = 0; k < dadconfs.length; k++) {
                        out.print(XMLHelper.escape(dadconfs[k]));
                        if (k + 1 < dadconfs.length) {
                            out.print(",");
                        }
                    }
                    out.print("\"");
                }
                printExtraAttributes(depArtifacts[j], out, " ");
                out.println("/>");
            }
        }
    }

    /**
     * Writes the extra attributes of the given {@link org.apache.ivy.util.extendable.ExtendableItem} to the given <tt>PrintWriter</tt>.
     *
     * @param item the {@link org.apache.ivy.util.extendable.ExtendableItem}, cannot be <tt>null</tt>
     * @param out the writer to use
     * @param prefix the string to write before writing the attributes (if any)
     */
    private static void printExtraAttributes(ExtendableItem item, PrintWriter out, String prefix) {
        printExtraAttributes(item.getQualifiedExtraAttributes(), out, prefix);
    }

    /**
     * Writes the specified <tt>Map</tt> containing the extra attributes to the given <tt>PrintWriter</tt>.
     *
     * @param extra the extra attributes, can be <tt>null</tt>
     * @param out the writer to use
     * @param prefix the string to write before writing the attributes (if any)
     */
    private static void printExtraAttributes(Map extra, PrintWriter out, String prefix) {
        if (extra == null) {
            return;
        }

        String delim = prefix;
        for (Iterator iter = extra.entrySet().iterator(); iter.hasNext();) {
            Map.Entry entry = (Map.Entry) iter.next();
            out.print(delim + entry.getKey() + "=\""
                    + XMLHelper.escape(entry.getValue().toString()) + "\"");
            delim = " ";
        }
    }

    private static void printPublications(ModuleDescriptor md, PrintWriter out) {
        out.println("\t<publications>");
        Artifact[] artifacts = md.getAllArtifacts();
        for (int i = 0; i < artifacts.length; i++) {
            out.print("\t\t<artifact");
            out.print(" name=\"" + XMLHelper.escape(artifacts[i].getName()) + "\"");
            out.print(" type=\"" + XMLHelper.escape(artifacts[i].getType()) + "\"");
            out.print(" ext=\"" + XMLHelper.escape(artifacts[i].getExt()) + "\"");
            out.print(" conf=\"" + XMLHelper.escape(getConfs(md, artifacts[i])) + "\"");
            printExtraAttributes(artifacts[i], out, " ");
            out.println("/>");
        }
        out.println("\t</publications>");
    }

    private static void printConfigurations(ModuleDescriptor md, PrintWriter out) {
        Configuration[] confs = md.getConfigurations();
        if (confs.length > 0) {
            out.println("\t<configurations>");
            for (int i = 0; i < confs.length; i++) {
                Configuration conf = confs[i];
                out.print("\t\t");
                printConfiguration(conf, out);
            }
            out.println("\t</configurations>");
        }
    }

    protected static void printConfiguration(Configuration conf, PrintWriter out) {
        out.print("<conf");
        out.print(" name=\"" + XMLHelper.escape(conf.getName()) + "\"");
        out.print(" visibility=\""
                + XMLHelper.escape(conf.getVisibility().toString()) + "\"");
        String description = conf.getDescription();
        if (description != null) {
            out.print(" description=\""
                    + XMLHelper.escape(description) + "\"");
        }
        String[] exts = conf.getExtends();
        if (exts.length > 0) {
            out.print(" extends=\"");
            for (int j = 0; j < exts.length; j++) {
                out.print(XMLHelper.escape(exts[j]));
                if (j + 1 < exts.length) {
                    out.print(",");
                }
            }
            out.print("\"");
        }
        if (!conf.isTransitive()) {
            out.println(" transitive=\"false\"");
        }
        if (conf.getDeprecated() != null) {
            out.print(" deprecated=\"" + XMLHelper.escape(conf.getDeprecated()) + "\"");
        }
        printExtraAttributes(conf, out, " ");
        out.println("/>");
    }

    private static void printInfoTag(ModuleDescriptor md, PrintWriter out) {
        ModuleRevisionId moduleRevisionId = md.getModuleRevisionId();
        out.println("\t<info organisation=\""
                + XMLHelper.escape(moduleRevisionId.getOrganisation())
                + "\"");
        out.println("\t\tmodule=\"" + XMLHelper.escape(moduleRevisionId.getName()) + "\"");

        ModuleRevisionId resolvedModuleRevisionId = md.getResolvedModuleRevisionId();
        String branch = resolvedModuleRevisionId.getBranch();
        if (branch != null) {
            out.println("\t\tbranch=\"" + XMLHelper.escape(branch) + "\"");
        }
        String revision = resolvedModuleRevisionId.getRevision();
        if (revision != null) {
            out.println("\t\trevision=\"" + XMLHelper.escape(revision) + "\"");
        }
        out.println("\t\tstatus=\"" + XMLHelper.escape(md.getStatus()) + "\"");
        out.println("\t\tpublication=\""
                + Ivy.DATE_FORMAT.format(md.getResolvedPublicationDate()) + "\"");
        if (md.isDefault()) {
            out.println("\t\tdefault=\"true\"");
        }
        if (md instanceof DefaultModuleDescriptor) {
            DefaultModuleDescriptor dmd = (DefaultModuleDescriptor) md;
            if (dmd.getNamespace() != null && !dmd.getNamespace().getName().equals("system")) {
                out.println("\t\tnamespace=\""
                        + XMLHelper.escape(dmd.getNamespace().getName()) + "\"");
            }
        }
        if (!md.getExtraAttributes().isEmpty()) {
            printExtraAttributes(md, out, "\t\t");
            out.println();
        }
        if (requireInnerInfoElement(md)) {
            out.println("\t>");
            ExtendsDescriptor[] parents = md.getInheritedDescriptors();
            for (int i = 0; i < parents.length; i++) {
                ExtendsDescriptor parent = parents[i];
                ModuleRevisionId mrid = parent.getParentRevisionId();
                out.print("\t\t<extends organisation=\"" + XMLHelper.escape(mrid.getOrganisation()) + "\""
                        + " module=\"" + XMLHelper.escape(mrid.getName()) + "\""
                        + " revision=\"" + XMLHelper.escape(mrid.getRevision()) + "\"");

                String location = parent.getLocation();
                if (location != null) {
                    out.print(" location=\"" + XMLHelper.escape(location) + "\"");
                }
                out.print(" extendType=\"" + StringUtils.join(parent.getExtendsTypes(), ",") + "\"");
                out.println("/>");
            }
            License[] licenses = md.getLicenses();
            for (int i = 0; i < licenses.length; i++) {
                License license = licenses[i];
                out.print("\t\t<license ");
                if (license.getName() != null) {
                    out.print("name=\"" + XMLHelper.escape(license.getName()) + "\" ");
                }
                if (license.getUrl() != null) {
                    out.print("url=\"" + XMLHelper.escape(license.getUrl()) + "\" ");
                }
                out.println("/>");
            }
            if (md.getHomePage() != null || md.getDescription() != null) {
                out.print("\t\t<description");
                if (md.getHomePage() != null) {
                    out.print(" homepage=\"" + XMLHelper.escape(md.getHomePage()) + "\"");
                }
                if (md.getDescription() != null && md.getDescription().trim().length() > 0) {
                    out.println(">");
                    out.println("\t\t" + XMLHelper.escape(md.getDescription()));
                    out.println("\t\t</description>");
                } else {
                    out.println(" />");
                }
            }
            for (Iterator it = md.getExtraInfo().entrySet().iterator(); it.hasNext();) {
                Map.Entry extraDescr = (Map.Entry) it.next();
                if (extraDescr.getValue() == null
                        || ((String) extraDescr.getValue()).length() == 0) {
                    continue;
                }
                out.print("\t\t<");
                out.print(extraDescr.getKey());
                out.print(">");
                out.print(XMLHelper.escape((String) extraDescr.getValue()));
                out.print("</");
                out.print(extraDescr.getKey());
                out.println(">");
            }
            out.println("\t</info>");
        } else {
            out.println("\t/>");
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
