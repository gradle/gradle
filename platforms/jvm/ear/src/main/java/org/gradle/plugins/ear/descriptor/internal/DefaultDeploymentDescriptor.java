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
import groovy.namespace.QName;
import groovy.util.Node;
import groovy.xml.XmlParser;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.XmlProvider;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.Cast;
import org.gradle.internal.IoActions;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ear.descriptor.DeploymentDescriptor;
import org.gradle.plugins.ear.descriptor.EarModule;
import org.gradle.plugins.ear.descriptor.EarSecurityRole;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;

import javax.inject.Inject;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.List;

import static org.gradle.plugins.ear.descriptor.internal.DeploymentDescriptorXmlWriter.writeDeploymentDescriptor;

public abstract class DefaultDeploymentDescriptor implements DeploymentDescriptor {

    private static final String ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD";
    private static final String ALLOW_ANY_EXTERNAL_DTD = "all";

    private final XmlTransformer transformer = new XmlTransformer();
    private final PathToFileResolver fileResolver;

    private final ObjectFactory objectFactory;

    private String fileName = "application.xml";

    @Inject
    public DefaultDeploymentDescriptor(PathToFileResolver fileResolver, ObjectFactory objectFactory) {
        this.fileResolver = fileResolver;
        this.objectFactory = objectFactory;
        getVersion().convention("6");
        getInitializeInOrder().convention(Boolean.FALSE);
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
    public abstract Property<String> getVersion();

    @Override
    public abstract Property<String> getApplicationName();

    @Override
    public abstract Property<Boolean> getInitializeInOrder();

    @Override
    public abstract Property<String> getDescription();

    @Override
    public abstract Property<String> getDisplayName();

    @Override
    public abstract Property<String> getLibraryDirectory();

    @Override
    public abstract SetProperty<EarModule> getModules();

    @Override
    public abstract SetProperty<EarSecurityRole> getSecurityRoles();

    @Override
    public abstract MapProperty<String, String> getModuleTypeMappings();

    @Override
    public DefaultDeploymentDescriptor module(EarModule module, String type) {
        getModules().add(module);
        getModuleTypeMappings().put(module.getPath().get(), type);
        return this;
    }

    @Override
    public DefaultDeploymentDescriptor module(String path, String type) {
        return module(newDefaultEarModule(path), type);
    }

    @Override
    public DefaultDeploymentDescriptor webModule(String path, String contextRoot) {
        getModules().add(newDefaultEarWebModule(path, contextRoot));
        getModuleTypeMappings().put(path, "web");
        return this;
    }

    @Override
    public DefaultDeploymentDescriptor securityRole(EarSecurityRole role) {
        getSecurityRoles().add(role);
        return this;
    }

    @Override
    public DeploymentDescriptor securityRole(String role) {
        getSecurityRoles().add(newDefaultEarSecurityRole(role, null));
        return this;
    }

    @Override
    public DeploymentDescriptor securityRole(Action<? super EarSecurityRole> action) {
        EarSecurityRole role = newDefaultEarSecurityRole(null, null);
        action.execute(role);
        getSecurityRoles().add(role);
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
            throw UncheckedException.throwAsUncheckedException(ex);
        }
    }

    @Override
    public DeploymentDescriptor readFrom(Reader reader) {
        try {
            Node appNode = createParser().parse(reader);
            getVersion().set((String) appNode.attribute("version"));
            for (final Node child : Cast.<List<Node>>uncheckedCast(appNode.children())) {
                String childLocalName = localNameOf(child);
                switch (childLocalName) {
                    case "application-name":
                        getApplicationName().set(child.text());
                        break;
                    case "initialize-in-order":
                        getInitializeInOrder().set(Boolean.parseBoolean(child.text()));
                        break;
                    case "description":
                        getDescription().set(child.text());
                        break;
                    case "display-name":
                        getDisplayName().set(child.text());
                        break;
                    case "library-directory":
                        getLibraryDirectory().set(child.text());
                        break;
                    case "module":
                        EarModule module = null;
                        for (Node moduleNode : Cast.<List<Node>>uncheckedCast(child.children())) {
                            String moduleNodeLocalName = localNameOf(moduleNode);
                            if (moduleNodeLocalName.equals("web")) {
                                String webUri = childNodeText(moduleNode, "web-uri");
                                String contextRoot = childNodeText(moduleNode, "context-root");
                                if (webUri != null && contextRoot != null) {
                                    module = newDefaultEarWebModule(webUri, contextRoot);
                                    getModules().add(module);
                                    getModuleTypeMappings().put(webUri, "web");
                                }
                            } else if (moduleNodeLocalName.equals("alt-dd")) {
                                assert module != null;
                                module.getAltDeployDescriptor().set(moduleNode.text());
                            } else {
                                String path = moduleNode.text();
                                module = newDefaultEarModule(path);
                                getModules().add(module);
                                getModuleTypeMappings().put(path, moduleNodeLocalName);
                            }
                        }
                        break;
                    case "security-role":
                        String roleName = childNodeText(child, "role-name");
                        String description = childNodeText(child, "description");
                        getSecurityRoles().add(newDefaultEarSecurityRole(roleName, description));
                        break;
                    default:
                        withXml(new Action<XmlProvider>() {
                            @Override
                            public void execute(XmlProvider xmlProvider) {
                                xmlProvider.asNode().append(child);
                            }
                        });
                        break;
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (SAXException ex) {
            throw UncheckedException.throwAsUncheckedException(ex);
        } finally {
            IoActions.closeQuietly(reader);
        }
        return this;
    }

    private DefaultEarWebModule newDefaultEarWebModule(String path, String contextRoot) {
        DefaultEarWebModule module = objectFactory.newInstance(DefaultEarWebModule.class);
        module.getPath().set(path);
        module.getContextRoot().set(contextRoot);
        return module;
    }

    private DefaultEarModule newDefaultEarModule(String path) {
        DefaultEarModule module = objectFactory.newInstance(DefaultEarModule.class);
        module.getPath().set(path);
        return module;
    }

    private DefaultEarSecurityRole newDefaultEarSecurityRole(String roleName, String description) {
        DefaultEarSecurityRole role = objectFactory.newInstance(DefaultEarSecurityRole.class);
        role.getRoleName().set(roleName);
        role.getDescription().set(description);
        return role;
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
        writeDeploymentDescriptor(this, transformer, fileResolver.resolve(path));
        return this;
    }

    @Override
    public DefaultDeploymentDescriptor writeTo(Writer writer) {
        writeDeploymentDescriptor(this, transformer, writer);
        return this;
    }

    // For tests
    XmlTransformer getTransformer() {
        return transformer;
    }
}
