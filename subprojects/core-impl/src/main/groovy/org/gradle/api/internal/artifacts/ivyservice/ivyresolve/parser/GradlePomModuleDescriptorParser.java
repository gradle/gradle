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

import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.module.descriptor.Configuration.Visibility;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolveEngine;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.namespace.NameSpaceHelper;
import org.apache.ivy.plugins.parser.ModuleDescriptorParser;
import org.apache.ivy.plugins.parser.ParserSettings;
import org.apache.ivy.plugins.parser.m2.PomDependencyMgt;
import org.apache.ivy.plugins.parser.m2.PomReader;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.repository.url.URLResource;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.util.Message;
import org.gradle.internal.UncheckedException;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * This a straight copy of org.apache.ivy.plugins.parser.m2.PomModuleDescriptorParser, with one change: we do NOT attempt to retrieve source and javadoc artifacts when parsing the POM. This cuts the
 * number of remote call in half to resolve a module.
 */
public final class GradlePomModuleDescriptorParser implements ModuleDescriptorParser {
    public void toIvyFile(InputStream is, Resource res, File destFile, ModuleDescriptor md)
            throws ParseException, IOException {
        throw new UnsupportedOperationException();
    }

    public boolean accept(Resource res) {
        return res.getName().endsWith(".pom") || res.getName().endsWith("pom.xml")
                || res.getName().endsWith("project.xml");
    }

    public String toString() {
        return "gradle pom parser";
    }

    public Artifact getMetadataArtifact(ModuleRevisionId mrid, Resource res) {
        return DefaultArtifact.newPomArtifact(mrid, new Date(res.getLastModified()));
    }

    public String getType() {
        return "pom";
    }

    public ModuleDescriptor parseDescriptor(ParserSettings ivySettings, URL descriptorURL,
                                            boolean validate) throws ParseException, IOException {
        URLResource resource = new URLResource(descriptorURL);
        return parseDescriptor(ivySettings, descriptorURL, resource, validate);
    }

    public ModuleDescriptor parseDescriptor(ParserSettings ivySettings, URL descriptorURL,
                                            Resource resource, boolean validate) throws ParseException, IOException {

        Resource res = encodedUrlResource(resource, descriptorURL);
        GradlePomModuleDescriptorBuilder mdBuilder = new GradlePomModuleDescriptorBuilder(this, res, ivySettings);

        try {
            PomReader domReader = new PomReader(descriptorURL, res);
            domReader.setProperty("parent.version", domReader.getParentVersion());
            domReader.setProperty("parent.groupId", domReader.getParentGroupId());
            domReader.setProperty("project.parent.version", domReader.getParentVersion());
            domReader.setProperty("project.parent.groupId", domReader.getParentGroupId());

            Map pomProperties = domReader.getPomProperties();
            for (Object o : pomProperties.entrySet()) {
                Map.Entry prop = (Map.Entry) o;
                domReader.setProperty((String) prop.getKey(), (String) prop.getValue());
                mdBuilder.addProperty((String) prop.getKey(), (String) prop.getValue());
            }

            ModuleDescriptor parentDescr = null;
            if (domReader.hasParent()) {
                //Is there any other parent properties?

                ModuleRevisionId parentModRevID = ModuleRevisionId.newInstance(
                        domReader.getParentGroupId(),
                        domReader.getParentArtifactId(),
                        domReader.getParentVersion());
                ResolvedModuleRevision parentModule = parseOtherPom(ivySettings, parentModRevID);
                if (parentModule != null) {
                    parentDescr = parentModule.getDescriptor();
                } else {
                    throw new IOException("Impossible to load parent for " + res.getName() + ". Parent=" + parentModRevID);
                }
                if (parentDescr != null) {
                    Map parentPomProps = GradlePomModuleDescriptorBuilder.extractPomProperties(parentDescr.getExtraInfo());
                    for (Object o : parentPomProps.entrySet()) {
                        Map.Entry prop = (Map.Entry) o;
                        domReader.setProperty((String) prop.getKey(), (String) prop.getValue());
                    }
                }
            }

            String groupId = domReader.getGroupId();
            String artifactId = domReader.getArtifactId();
            String version = domReader.getVersion();
            ModuleScopedParserSettings scopedSettings = (ModuleScopedParserSettings) ivySettings;
            mdBuilder.setModuleRevId(scopedSettings.getCurrentRevisionId(), groupId, artifactId, version);

            mdBuilder.setHomePage(domReader.getHomePage());
            mdBuilder.setDescription(domReader.getDescription());
            mdBuilder.setLicenses(domReader.getLicenses());

            ModuleRevisionId relocation = domReader.getRelocation();

            if (relocation != null) {
                if (groupId != null && artifactId != null
                        && artifactId.equals(relocation.getName())
                        && groupId.equals(relocation.getOrganisation())) {
                    Message.error("Relocation to an other version number not supported in ivy : "
                            + mdBuilder.getModuleDescriptor().getModuleRevisionId()
                            + " relocated to " + relocation
                            + ". Please update your dependency to directly use the right version.");
                    Message.warn("Resolution will only pick dependencies of the relocated element."
                            + "  Artefact and other metadata will be ignored.");
                    ResolvedModuleRevision relocatedModule = parseOtherPom(ivySettings, relocation);
                    if (relocatedModule == null) {
                        throw new ParseException("impossible to load module "
                                + relocation + " to which "
                                + mdBuilder.getModuleDescriptor().getModuleRevisionId()
                                + " has been relocated", 0);
                    }
                    DependencyDescriptor[] dds = relocatedModule.getDescriptor().getDependencies();
                    for (DependencyDescriptor dd : dds) {
                        mdBuilder.addDependency(dd);
                    }
                } else {
                    Message.info(mdBuilder.getModuleDescriptor().getModuleRevisionId()
                            + " is relocated to " + relocation
                            + ". Please update your dependencies.");
                    Message.verbose("Relocated module will be considered as a dependency");
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
                domReader.setProperty("project.groupId", groupId);
                domReader.setProperty("pom.groupId", groupId);
                domReader.setProperty("groupId", groupId);
                domReader.setProperty("project.artifactId", artifactId);
                domReader.setProperty("pom.artifactId", artifactId);
                domReader.setProperty("artifactId", artifactId);
                domReader.setProperty("project.version", version);
                domReader.setProperty("pom.version", version);
                domReader.setProperty("version", version);

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

                for (Object o : domReader.getDependencyMgt()) {
                    PomDependencyMgt dep = (PomDependencyMgt) o;
                    if ("import".equals(dep.getScope())) {
                        ModuleRevisionId importModRevID = ModuleRevisionId.newInstance(
                                dep.getGroupId(),
                                dep.getArtifactId(),
                                dep.getVersion());
                        ResolvedModuleRevision importModule = parseOtherPom(ivySettings, importModRevID);
                        if (importModule != null) {
                            ModuleDescriptor importDescr = importModule.getDescriptor();

                            // add dependency management info from imported module
                            List depMgt = GradlePomModuleDescriptorBuilder.getDependencyManagements(importDescr);
                            for (Object aDepMgt : depMgt) {
                                mdBuilder.addDependencyMgt((PomDependencyMgt) aDepMgt);
                            }
                        } else {
                            throw new IOException("Impossible to import module for " + res.getName() + "."
                                    + " Import=" + importModRevID);
                        }

                    } else {
                        mdBuilder.addDependencyMgt(dep);
                    }
                }

                for (Object o : domReader.getDependencies()) {
                    PomReader.PomDependencyData dep = (PomReader.PomDependencyData) o;
                    mdBuilder.addDependency(dep);
                }

                if (parentDescr != null) {
                    for (int i = 0; i < parentDescr.getDependencies().length; i++) {
                        mdBuilder.addDependency(parentDescr.getDependencies()[i]);
                    }
                }

                for (Object o : domReader.getPlugins()) {
                    PomReader.PomPluginElement plugin = (PomReader.PomPluginElement) o;
                    mdBuilder.addPlugin(plugin);
                }

                mdBuilder.addMainArtifact(artifactId, domReader.getPackaging());
            }
        } catch (SAXException e) {
            throw newParserException(e);
        }

        return mdBuilder.getModuleDescriptor();
    }

    private ResolvedModuleRevision parseOtherPom(ParserSettings ivySettings,
                                                 ModuleRevisionId parentModRevID) throws ParseException {
        DependencyDescriptor dd = new DefaultDependencyDescriptor(parentModRevID, true);
        ResolveData data = IvyContext.getContext().getResolveData();
        if (data == null) {
            ResolveEngine engine = IvyContext.getContext().getIvy().getResolveEngine();
            ResolveOptions options = new ResolveOptions();
            options.setDownload(false);
            data = new ResolveData(engine, options);
        }

        DependencyResolver resolver = ivySettings.getResolver(parentModRevID);
        if (resolver == null) {
            // TODO: Throw exception here?
            return null;
        } else {
            dd = NameSpaceHelper.toSystem(dd, ivySettings.getContextNamespace());
            return resolver.getDependency(dd, data);
        }
    }

    private ParseException newParserException(Exception e) {
        Message.error(e.getMessage());
        ParseException pe = new ParseException(e.getMessage(), 0);
        pe.initCause(e);
        return pe;
    }

    private Resource encodedUrlResource(final Resource base, final URL url) {
        Object proxy = Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Resource.class}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("getName".equals(method.getName())) {
                    return url.toString();
                }
                try {
                    return method.invoke(base, args);
                } catch (InvocationTargetException e) {
                    throw UncheckedException.throwAsUncheckedException(e.getTargetException());
                }
            }
        });
        return (Resource) proxy;
    }
}
