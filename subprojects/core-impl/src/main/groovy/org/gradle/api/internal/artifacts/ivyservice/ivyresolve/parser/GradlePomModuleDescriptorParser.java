/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.module.descriptor.Configuration.Visibility;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.parser.m2.PomDependencyMgt;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.MutableModuleVersionMetaData;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.LocallyAvailableExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

/**
 * This a straight copy of org.apache.ivy.plugins.parser.m2.PomModuleDescriptorParser, with one change: we do NOT attempt to retrieve source and javadoc artifacts when parsing the POM. This cuts the
 * number of remote call in half to resolve a module.
 */
public final class GradlePomModuleDescriptorParser extends AbstractModuleDescriptorParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(GradlePomModuleDescriptorParser.class);

    public boolean accept(ExternalResource res) {
        return res.getName().endsWith(".pom") || res.getName().endsWith("pom.xml")
                || res.getName().endsWith("project.xml");
    }

    @Override
    protected String getTypeName() {
        return "POM";
    }

    public String toString() {
        return "gradle pom parser";
    }

    protected MutableModuleVersionMetaData doParseDescriptor(DescriptorParseContext parserSettings, LocallyAvailableExternalResource resource, boolean validate) throws IOException, ParseException, SAXException {
        GradlePomModuleDescriptorBuilder mdBuilder = new GradlePomModuleDescriptorBuilder(resource, parserSettings);

        PomReader pomReader = new PomReader(resource);
        pomReader.setProperty("parent.version", pomReader.getParentVersion());
        pomReader.setProperty("parent.groupId", pomReader.getParentGroupId());
        pomReader.setProperty("project.parent.version", pomReader.getParentVersion());
        pomReader.setProperty("project.parent.groupId", pomReader.getParentGroupId());

        Map pomProperties = pomReader.getPomProperties();
        for (Object o : pomProperties.entrySet()) {
            Map.Entry prop = (Map.Entry) o;
            pomReader.setProperty((String) prop.getKey(), (String) prop.getValue());
            mdBuilder.addProperty((String) prop.getKey(), (String) prop.getValue());
        }

        ModuleDescriptor parentDescr = null;
        if (pomReader.hasParent()) {
            //Is there any other parent properties?

            ModuleRevisionId parentModRevID = ModuleRevisionId.newInstance(
                    pomReader.getParentGroupId(),
                    pomReader.getParentArtifactId(),
                    pomReader.getParentVersion());
            parentDescr = parseOtherPom(parserSettings, parentModRevID);
            if (parentDescr == null) {
                throw new IOException("Impossible to load parent for " + resource.getName() + ". Parent=" + parentModRevID);
            }
            Map parentPomProps = GradlePomModuleDescriptorBuilder.extractPomProperties(parentDescr.getExtraInfo());
            for (Object o : parentPomProps.entrySet()) {
                Map.Entry prop = (Map.Entry) o;
                pomReader.setProperty((String) prop.getKey(), (String) prop.getValue());
            }
        }

        String groupId = pomReader.getGroupId();
        String artifactId = pomReader.getArtifactId();
        String version = pomReader.getVersion();
        mdBuilder.setModuleRevId(parserSettings.getCurrentRevisionId(), groupId, artifactId, version);

        mdBuilder.setHomePage(pomReader.getHomePage());
        mdBuilder.setDescription(pomReader.getDescription());
        mdBuilder.setLicenses(pomReader.getLicenses());

        ModuleRevisionId relocation = pomReader.getRelocation();

        if (relocation != null) {
            if (groupId != null && artifactId != null
                    && artifactId.equals(relocation.getName())
                    && groupId.equals(relocation.getOrganisation())) {
                LOGGER.error("POM relocation to an other version number is not fully supported in Gradle : {} relocated to {}.",
                        mdBuilder.getModuleDescriptor().getModuleRevisionId(), relocation);
                LOGGER.warn("Please update your dependency to directly use the correct version '{}'.", relocation);
                LOGGER.warn("Resolution will only pick dependencies of the relocated element.  Artifacts and other metadata will be ignored.");
                ModuleDescriptor relocatedModule = parseOtherPom(parserSettings, relocation);
                if (relocatedModule == null) {
                    throw new ParseException("impossible to load module "
                            + relocation + " to which "
                            + mdBuilder.getModuleDescriptor().getModuleRevisionId()
                            + " has been relocated", 0);
                }
                DependencyDescriptor[] dds = relocatedModule.getDependencies();
                for (DependencyDescriptor dd : dds) {
                    mdBuilder.addDependency(dd);
                }
            } else {
                LOGGER.info(mdBuilder.getModuleDescriptor().getModuleRevisionId()
                        + " is relocated to " + relocation
                        + ". Please update your dependencies.");
                LOGGER.debug("Relocated module will be considered as a dependency");
                DefaultDependencyDescriptor dd = new DefaultDependencyDescriptor(mdBuilder.getModuleDescriptor(), relocation, true, false, true);
                /* Map all public dependencies */
                Configuration[] m2Confs = GradlePomModuleDescriptorBuilder.MAVEN2_CONFIGURATIONS;
                for (Configuration m2Conf : m2Confs) {
                    if (Visibility.PUBLIC.equals(m2Conf.getVisibility())) {
                        dd.addDependencyConfiguration(m2Conf.getName(), m2Conf.getName());
                    }
                }
                mdBuilder.addDependency(dd);
            }
        } else {
            pomReader.setProperty("project.groupId", groupId);
            pomReader.setProperty("pom.groupId", groupId);
            pomReader.setProperty("groupId", groupId);
            pomReader.setProperty("project.artifactId", artifactId);
            pomReader.setProperty("pom.artifactId", artifactId);
            pomReader.setProperty("artifactId", artifactId);
            pomReader.setProperty("project.version", version);
            pomReader.setProperty("pom.version", version);
            pomReader.setProperty("version", version);

            if (parentDescr != null) {
                mdBuilder.addExtraInfos(parentDescr.getExtraInfo());

                // add dependency management info from parent
                List depMgt = GradlePomModuleDescriptorBuilder.getDependencyManagements(parentDescr);
                for (Object aDepMgt : depMgt) {
                    mdBuilder.addDependencyMgt((PomDependencyMgt) aDepMgt);
                }

                // add plugins from parent
                List /*<PomDependencyMgt>*/ plugins = GradlePomModuleDescriptorBuilder.getPlugins(parentDescr);
                for (Object plugin : plugins) {
                    mdBuilder.addPlugin((PomDependencyMgt) plugin);
                }
            }

            for (Object o : pomReader.getDependencyMgt()) {
                PomDependencyMgt dep = (PomDependencyMgt) o;
                if ("import".equals(dep.getScope())) {
                    ModuleRevisionId importModRevID = ModuleRevisionId.newInstance(
                            dep.getGroupId(),
                            dep.getArtifactId(),
                            dep.getVersion());
                    ModuleDescriptor importDescr = parseOtherPom(parserSettings, importModRevID);
                    if (importDescr == null) {
                        throw new IOException("Impossible to import module for " + resource.getName() + "."
                                + " Import=" + importModRevID);
                    }
                    // add dependency management info from imported module
                    List depMgt = GradlePomModuleDescriptorBuilder.getDependencyManagements(importDescr);
                    for (Object aDepMgt : depMgt) {
                        mdBuilder.addDependencyMgt((PomDependencyMgt) aDepMgt);
                    }

                } else {
                    mdBuilder.addDependencyMgt(dep);
                }
            }

            for (Object o : pomReader.getDependencies()) {
                PomReader.PomDependencyData dep = (PomReader.PomDependencyData) o;
                mdBuilder.addDependency(dep);
            }

            if (parentDescr != null) {
                for (int i = 0; i < parentDescr.getDependencies().length; i++) {
                    mdBuilder.addDependency(parentDescr.getDependencies()[i]);
                }
            }

            for (Object o : pomReader.getPlugins()) {
                PomReader.PomPluginElement plugin = (PomReader.PomPluginElement) o;
                mdBuilder.addPlugin(plugin);
            }

            mdBuilder.addMainArtifact(artifactId, pomReader.getPackaging());
        }

        DefaultModuleDescriptor moduleDescriptor = mdBuilder.getModuleDescriptor();
        return new ModuleDescriptorAdapter(moduleDescriptor.getModuleRevisionId(), moduleDescriptor);
    }

    private ModuleDescriptor parseOtherPom(DescriptorParseContext ivySettings,
                                           ModuleRevisionId parentModRevID) throws ParseException {
        return ivySettings.getModuleDescriptor(parentModRevID);
    }
}
