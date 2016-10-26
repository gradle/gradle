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
package org.gradle.plugins.ear.descriptor.internal;

import groovy.lang.Closure;
import groovy.util.Node;
import groovy.util.XmlParser;
import groovy.xml.QName;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.XmlProvider;
import org.gradle.api.internal.DomNode;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ear.descriptor.DeploymentDescriptor;
import org.gradle.plugins.ear.descriptor.EarModule;
import org.gradle.plugins.ear.descriptor.EarSecurityRole;
import org.gradle.plugins.ear.descriptor.EarWebModule;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;

import javax.inject.Inject;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultDeploymentDescriptor implements DeploymentDescriptor {

    private static final String ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD";
    private static final String ALLOW_ANY_EXTERNAL_DTD = "all";

    private final XmlTransformer transformer = new XmlTransformer();
    private final PathToFileResolver fileResolver;
    private final Instantiator instantiator;

    private String fileName = "application.xml";
    private String version = "6";
    private String applicationName;
    private Boolean initializeInOrder = Boolean.FALSE;
    private String description;
    private String displayName;
    private String libraryDirectory;
    private Set<EarModule> modules = new LinkedHashSet<EarModule>();
    private Set<EarSecurityRole> securityRoles = new LinkedHashSet<EarSecurityRole>();
    private Map<String, String> moduleTypeMappings = new LinkedHashMap<String, String>();

    @Inject
    public DefaultDeploymentDescriptor(PathToFileResolver fileResolver, Instantiator instantiator) {
        this.fileResolver = fileResolver;
        this.instantiator = instantiator;
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    @Override
    public void setFileName(String fileName) {
        this.fileName = fileName;
        readFrom(new File("META-INF", fileName));
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String getApplicationName() {
        return applicationName;
    }

    @Override
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    @Override
    public Boolean getInitializeInOrder() {
        return initializeInOrder;
    }

    @Override
    public void setInitializeInOrder(Boolean initializeInOrder) {
        this.initializeInOrder = initializeInOrder;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getLibraryDirectory() {
        return libraryDirectory;
    }

    @Override
    public void setLibraryDirectory(String libraryDirectory) {
        this.libraryDirectory = libraryDirectory;
    }

    @Override
    public Set<EarModule> getModules() {
        return modules;
    }

    @Override
    public void setModules(Set<EarModule> modules) {
        this.modules = modules;
    }

    @Override
    public Set<EarSecurityRole> getSecurityRoles() {
        return securityRoles;
    }

    @Override
    public void setSecurityRoles(Set<EarSecurityRole> securityRoles) {
        this.securityRoles = securityRoles;
    }

    @Override
    public Map<String, String> getModuleTypeMappings() {
        return moduleTypeMappings;
    }

    @Override
    public void setModuleTypeMappings(Map<String, String> moduleTypeMappings) {
        this.moduleTypeMappings = moduleTypeMappings;
    }

    @Override
    public DefaultDeploymentDescriptor module(EarModule module, String type) {
        modules.add(module);
        moduleTypeMappings.put(module.getPath(), type);
        return this;
    }

    @Override
    public DefaultDeploymentDescriptor module(String path, String type) {
        return module(new DefaultEarModule(path), type);
    }

    @Override
    public DefaultDeploymentDescriptor webModule(String path, String contextRoot) {
        modules.add(new DefaultEarWebModule(path, contextRoot));
        moduleTypeMappings.put(path, "web");
        return this;
    }

    @Override
    public DefaultDeploymentDescriptor securityRole(EarSecurityRole role) {
        securityRoles.add(role);
        return this;
    }

    @Override
    public DeploymentDescriptor securityRole(String role) {
        securityRoles.add(new DefaultEarSecurityRole(role));
        return this;
    }

    @Override
    public DeploymentDescriptor securityRole(Action<? super EarSecurityRole> action) {
        EarSecurityRole role = instantiator.newInstance(DefaultEarSecurityRole.class);
        action.execute(role);
        securityRoles.add(role);
        return this;
    }

    @Override
    public DeploymentDescriptor withXml(Closure closure) {
        transformer.addAction(closure);
        return this;
    }

    @Override
    public DeploymentDescriptor withXml(Action<? super XmlProvider> action) {
        transformer.addAction(action);
        return this;
    }

    @Override
    public boolean readFrom(Object path) {
        if (fileResolver == null) {
            return false;
        }
        File descriptorFile = fileResolver.resolve(path);
        if (descriptorFile == null || !descriptorFile.exists()) {
            return false;
        }
        try {
            FileReader reader = new FileReader(descriptorFile);
            readFrom(reader);
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static XmlParser createParser() {
        try {
            XmlParser parser = new XmlParser(false, true, true);
            try {
                // If not set for >= JAXP 1.5 / Java8 won't allow referencing DTDs, e.g.
                // using http URLs, because Groovy's XmlParser requests FEATURE_SECURE_PROCESSING
                parser.setProperty(ACCESS_EXTERNAL_DTD, ALLOW_ANY_EXTERNAL_DTD);
            } catch (SAXNotRecognizedException ignore) {
                // property requires >= JAXP 1.5 / Java8
            }
            return parser;
        } catch (Exception ex) {
            throw new UncheckedException(ex);
        }
    }

    @Override
    public DeploymentDescriptor readFrom(Reader reader) {
        try {
            Node appNode = createParser().parse(reader);
            version = (String) appNode.attribute("version");
            for (final Node child : Cast.<List<Node>>uncheckedCast(appNode.children())) {
                String childLocalName = localNameOf(child);
                if (childLocalName.equals("application-name")) {

                    applicationName = child.text();

                } else if (childLocalName.equals("initialize-in-order")) {

                    initializeInOrder = Boolean.valueOf(child.text());

                } else if (childLocalName.equals("description")) {

                    description = child.text();

                } else if (childLocalName.equals("display-name")) {

                    displayName = child.text();

                } else if (childLocalName.equals("library-directory")) {

                    libraryDirectory = child.text();

                } else if (childLocalName.equals("module")) {

                    EarModule module = null;
                    for (Node moduleNode : Cast.<List<Node>>uncheckedCast(child.children())) {
                        String moduleNodeLocalName = localNameOf(moduleNode);
                        if (moduleNodeLocalName.equals("web")) {
                            String webUri = childNodeText(moduleNode, "web-uri");
                            String contextRoot = childNodeText(moduleNode, "context-root");
                            module = new DefaultEarWebModule(webUri, contextRoot);
                            modules.add(module);
                            moduleTypeMappings.put(module.getPath(), "web");
                        } else if (moduleNodeLocalName.equals("alt-dd")) {
                            assert module != null;
                            module.setAltDeployDescriptor(moduleNode.text());
                        } else {
                            module = new DefaultEarModule(moduleNode.text());
                            modules.add(module);
                            moduleTypeMappings.put(module.getPath(), moduleNodeLocalName);
                        }
                    }

                } else if (childLocalName.equals("security-role")) {

                    String roleName = childNodeText(child, "role-name");
                    String description = childNodeText(child, "description");
                    securityRoles.add(new DefaultEarSecurityRole(roleName, description));

                } else {
                    withXml(new Action<XmlProvider>() {
                        @Override
                        public void execute(XmlProvider xmlProvider) {
                            xmlProvider.asNode().append(child);
                        }
                    });
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (SAXException ex) {
            throw new UncheckedException(ex);
        } finally {
            IOUtils.closeQuietly(reader);
        }
        return this;
    }

    private static String childNodeText(Node root, String name) {
        for (Node child : Cast.<List<Node>>uncheckedCast(root.children())) {
            if (localNameOf(child).equals(name)) {
                return child.text();
            }
        }
        return null;
    }

    private static String localNameOf(Node node) {
        return node.name() instanceof QName ? ((QName) node.name()).getLocalPart() : String.valueOf(node.name());
    }

    @Override
    public DefaultDeploymentDescriptor writeTo(Object path) {
        transformer.transform(toXmlNode(), fileResolver.resolve(path));
        return this;
    }

    @Override
    public DefaultDeploymentDescriptor writeTo(Writer writer) {
        transformer.transform(toXmlNode(), writer);
        return this;
    }

    private DomNode toXmlNode() {
        DomNode root = new DomNode(nodeNameFor("application"));
        root.attributes().put("version", version);
        if (!"1.3".equals(version)) {
            root.attributes().put("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        }
        if ("1.3".equals(version)) {
            root.setPublicId("-//Sun Microsystems, Inc.//DTD J2EE Application 1.3//EN");
            root.setSystemId("http://java.sun.com/dtd/application_1_3.dtd");
        } else if ("1.4".equals(version)) {
            root.attributes().put("xsi:schemaLocation", "http://java.sun.com/xml/ns/j2ee http://java.sun.com/xml/ns/j2ee/application_1_4.xsd");
        } else if ("5".equals(version) || "6".equals(version)) {
            root.attributes().put("xsi:schemaLocation", "http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/application_" + version + ".xsd");
        } else if ("7".equals(version)) {
            root.attributes().put("xsi:schemaLocation", "http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/application_" + version + ".xsd");
        }
        if (applicationName != null) {
            new Node(root, nodeNameFor("application-name"), applicationName);
        }
        if (description != null) {
            new Node(root, nodeNameFor("description"), description);
        }
        if (displayName != null) {
            new Node(root, nodeNameFor("display-name"), displayName);
        }
        if (initializeInOrder != null && initializeInOrder) {
            new Node(root, nodeNameFor("initialize-in-order"), initializeInOrder);
        }
        for (EarModule module : modules) {
            Node moduleNode = new Node(root, nodeNameFor("module"));
            module.toXmlNode(moduleNode, moduleNameFor(module));
        }
        if (securityRoles != null) {
            for (EarSecurityRole role : securityRoles) {
                Node roleNode = new Node(root, nodeNameFor("security-role"));
                if (role.getDescription() != null) {
                    new Node(roleNode, nodeNameFor("description"), role.getDescription());
                }
                new Node(roleNode, nodeNameFor("role-name"), role.getRoleName());
            }
        }
        if (libraryDirectory != null) {
            new Node(root, nodeNameFor("library-directory"), libraryDirectory);
        }
        return root;
    }

    private Object moduleNameFor(EarModule module) {
        String name = moduleTypeMappings.get(module.getPath());
        if (name == null) {
            if (module instanceof EarWebModule) {
                name = "web";
            } else {
                // assume EJB is the most common kind of EAR deployment
                name = "ejb";
            }
        }
        return nodeNameFor(name);
    }

    private Object nodeNameFor(String name) {
        if ("1.3".equals(version)) {
            return name;
        } else if ("1.4".equals(version)) {
            return new QName("http://java.sun.com/xml/ns/j2ee", name);
        } else if ("5".equals(version) || "6".equals(version)) {
            return new QName("http://java.sun.com/xml/ns/javaee", name);
        } else if ("7".equals(version)) {
            return new QName("http://xmlns.jcp.org/xml/ns/javaee", name);
        } else {
            return new QName(name);
        }
    }

    // For tests
    XmlTransformer getTransformer() {
        return transformer;
    }
}
