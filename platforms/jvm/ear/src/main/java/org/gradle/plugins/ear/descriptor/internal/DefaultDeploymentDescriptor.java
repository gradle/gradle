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
import org.gradle.api.XmlProvider;
import org.gradle.api.internal.DomNode;
import org.gradle.api.internal.provider.EagerPropertySupport;
import org.gradle.api.internal.provider.MapPropertyInternal;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.IoActions;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.instrumentation.api.annotations.BackedByLazyProperty;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ear.descriptor.DeploymentDescriptor;
import org.gradle.plugins.ear.descriptor.DeploymentDescriptorInputs;
import org.gradle.plugins.ear.descriptor.EarModule;
import org.gradle.plugins.ear.descriptor.EarSecurityRole;
import org.gradle.plugins.ear.descriptor.EarWebModule;
import org.jspecify.annotations.Nullable;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;

import javax.inject.Inject;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class DefaultDeploymentDescriptor implements DeploymentDescriptor {

    private static final String ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD";
    private static final String ALLOW_ANY_EXTERNAL_DTD = "all";

    // Pattern to match plausible Jakarta EE Versions "9", "10", "11" ... "99"
    private static final Pattern JAKARTA_VERSION_PATTERN = Pattern.compile("9|[1-9][0-9]");

    private final XmlTransformer transformer = new XmlTransformer();
    private final PathToFileResolver fileResolver;

    private ObjectFactory objectFactory;

    private DeploymentDescriptorInputs lazyProperties;

    @Inject
    @SuppressWarnings("this-escape")
    public DefaultDeploymentDescriptor(PathToFileResolver fileResolver, ObjectFactory objectFactory) {
        this.fileResolver = fileResolver;
        this.objectFactory = objectFactory;
        this.lazyProperties = storageWithDefaults(objectFactory.newInstance(DeploymentDescriptorInputs.class));
    }

    /**
     * Stores the values of this descriptor in the given lazy properties from now on.
     * Values stored before are not moved, so call this before the descriptor is configured.
     */
    public void attach(DeploymentDescriptorInputs target) {
        lazyProperties = storageWithDefaults(target);
    }

    /**
     * Stores the values of this descriptor in the given lazy properties from now on.
     * The current values are moved there and replace what the lazy properties held.
     */
    public void attachWithValues(DeploymentDescriptorInputs target) {
        if (target != lazyProperties) {
            moveTo(storageWithDefaults(target));
        }
    }

    /**
     * Stores the values of this descriptor in lazy properties of its own again, if it is attached to the given ones.
     * The current values are copied, so later changes on either side do not reach the other.
     */
    public void detach(DeploymentDescriptorInputs current) {
        if (current == lazyProperties) {
            moveTo(storageWithDefaults(objectFactory.newInstance(DeploymentDescriptorInputs.class)));
        }
    }

    public DeploymentDescriptorInputs getLazyProperties() {
        return lazyProperties;
    }

    private static DeploymentDescriptorInputs storageWithDefaults(DeploymentDescriptorInputs storage) {
        storage.getFileName().convention("application.xml");
        storage.getVersion().convention("6");
        storage.getInitializeInOrder().convention(false);
        return storage;
    }

    private void moveTo(DeploymentDescriptorInputs target) {
        DeploymentDescriptorInputs source = lazyProperties;
        copyValue(source.getFileName(), target.getFileName());
        copyValue(source.getVersion(), target.getVersion());
        copyValue(source.getApplicationName(), target.getApplicationName());
        copyValue(source.getInitializeInOrder(), target.getInitializeInOrder());
        copyValue(source.getDescription(), target.getDescription());
        copyValue(source.getDisplayName(), target.getDisplayName());
        copyValue(source.getLibraryDirectory(), target.getLibraryDirectory());
        target.getModules().set(currentValueOf(source.getModules()));
        target.getSecurityRoles().set(currentValueOf(source.getSecurityRoles()));
        target.getModuleTypeMappings().set(currentValueOf(source.getModuleTypeMappings()));
        lazyProperties = target;
    }

    private static <T> void copyValue(Property<T> source, Property<T> target) {
        target.set(currentValueOf(source));
    }

    // Fixed values are copied, values from a task output stay providers, so nothing is calculated too early
    private static <T> ProviderInternal<? extends T> currentValueOf(Provider<T> property) {
        return Cast.<ProviderInternal<T>>uncheckedNonnullCast(property).calculateExecutionTimeValue().toProvider();
    }

    private static void putWithoutReading(MapProperty<String, String> property, String key, String value) {
        Cast.<MapPropertyInternal<String, String>>uncheckedNonnullCast(property).insert(key, value);
    }

    @Override
    @BackedByLazyProperty("lazyProperties.fileName")
    public String getFileName() {
        return lazyProperties.getFileName().getOrNull();
    }

    @Override
    public void setFileName(String fileName) {
        EagerPropertySupport.setEager(lazyProperties.getFileName(), fileName);
        readFrom(new File("META-INF", fileName));
    }

    @Override
    @BackedByLazyProperty("lazyProperties.version")
    public String getVersion() {
        return lazyProperties.getVersion().getOrNull();
    }

    @Override
    public void setVersion(String version) {
        EagerPropertySupport.setEager(lazyProperties.getVersion(), version);
    }

    @Override
    @BackedByLazyProperty("lazyProperties.applicationName")
    public String getApplicationName() {
        return lazyProperties.getApplicationName().getOrNull();
    }

    @Override
    public void setApplicationName(String applicationName) {
        EagerPropertySupport.setEager(lazyProperties.getApplicationName(), applicationName);
    }

    @Override
    @BackedByLazyProperty("lazyProperties.initializeInOrder")
    public Boolean getInitializeInOrder() {
        return lazyProperties.getInitializeInOrder().getOrNull();
    }

    @Override
    public void setInitializeInOrder(Boolean initializeInOrder) {
        EagerPropertySupport.setEager(lazyProperties.getInitializeInOrder(), initializeInOrder);
    }

    @Override
    @BackedByLazyProperty("lazyProperties.description")
    public String getDescription() {
        return lazyProperties.getDescription().getOrNull();
    }

    @Override
    public void setDescription(String description) {
        EagerPropertySupport.setEager(lazyProperties.getDescription(), description);
    }

    @Override
    @BackedByLazyProperty("lazyProperties.displayName")
    public String getDisplayName() {
        return lazyProperties.getDisplayName().getOrNull();
    }

    @Override
    public void setDisplayName(String displayName) {
        EagerPropertySupport.setEager(lazyProperties.getDisplayName(), displayName);
    }

    @Override
    @BackedByLazyProperty("lazyProperties.libraryDirectory")
    public String getLibraryDirectory() {
        return lazyProperties.getLibraryDirectory().getOrNull();
    }

    @Override
    public void setLibraryDirectory(String libraryDirectory) {
        EagerPropertySupport.setEager(lazyProperties.getLibraryDirectory(), libraryDirectory);
    }

    @Override
    @BackedByLazyProperty("lazyProperties.modules")
    public Set<EarModule> getModules() {
        return EagerPropertySupport.conventionKeepingViewOrNull(lazyProperties.getModules());
    }

    @Override
    public void setModules(Set<EarModule> modules) {
        EagerPropertySupport.setEager(lazyProperties.getModules(), modules);
    }

    @Override
    @BackedByLazyProperty("lazyProperties.securityRoles")
    public Set<EarSecurityRole> getSecurityRoles() {
        return EagerPropertySupport.conventionKeepingViewOrNull(lazyProperties.getSecurityRoles());
    }

    @Override
    public void setSecurityRoles(Set<EarSecurityRole> securityRoles) {
        EagerPropertySupport.setEager(lazyProperties.getSecurityRoles(), securityRoles);
    }

    @Override
    @BackedByLazyProperty("lazyProperties.moduleTypeMappings")
    public Map<String, String> getModuleTypeMappings() {
        MapProperty<String, String> mappings = lazyProperties.getModuleTypeMappings();
        return mappings.isPresent() ? EagerPropertySupport.conventionKeepingView(mappings) : null;
    }

    @Override
    public void setModuleTypeMappings(Map<String, String> moduleTypeMappings) {
        EagerPropertySupport.setEager(lazyProperties.getModuleTypeMappings(), moduleTypeMappings);
    }

    @Override
    public DefaultDeploymentDescriptor module(EarModule module, String type) {
        addModule(module, type);
        return this;
    }

    private void addModule(EarModule module, String type) {
        EagerPropertySupport.appendAll(lazyProperties.getModules(), Collections.singleton(module));
        // A map property rejects null keys. A module without a path, read from a web module without web-uri, keeps the type of its class
        if (module.getPath() != null) {
            putWithoutReading(lazyProperties.getModuleTypeMappings(), module.getPath(), type);
        }
    }

    @Override
    public DefaultDeploymentDescriptor module(String path, String type) {
        return module(new DefaultEarModule(path), type);
    }

    @Override
    public DefaultDeploymentDescriptor webModule(String path, String contextRoot) {
        addModule(new DefaultEarWebModule(path, contextRoot), "web");
        return this;
    }

    @Override
    public DefaultDeploymentDescriptor securityRole(EarSecurityRole role) {
        addSecurityRole(role);
        return this;
    }

    @Override
    public DeploymentDescriptor securityRole(String role) {
        addSecurityRole(new DefaultEarSecurityRole(role));
        return this;
    }

    @Override
    public DeploymentDescriptor securityRole(Action<? super EarSecurityRole> action) {
        EarSecurityRole role = objectFactory.newInstance(DefaultEarSecurityRole.class);
        action.execute(role);
        addSecurityRole(role);
        return this;
    }

    private void addSecurityRole(EarSecurityRole role) {
        EagerPropertySupport.appendAll(lazyProperties.getSecurityRoles(), Collections.singleton(role));
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
    @SuppressWarnings("DefaultCharset") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
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
            throw UncheckedException.throwAsUncheckedException(e);
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
            setVersion((String) appNode.attribute("version"));
            for (final Node child : Cast.<List<Node>>uncheckedCast(appNode.children())) {
                String childLocalName = localNameOf(child);
                switch (childLocalName) {
                    case "application-name":

                        setApplicationName(child.text());

                        break;
                    case "initialize-in-order":

                        setInitializeInOrder(Boolean.parseBoolean(child.text()));

                        break;
                    case "description":

                        setDescription(child.text());

                        break;
                    case "display-name":

                        setDisplayName(child.text());

                        break;
                    case "library-directory":

                        setLibraryDirectory(child.text());

                        break;
                    case "module":

                        EarModule module = null;
                        for (Node moduleNode : Cast.<List<Node>>uncheckedCast(child.children())) {
                            String moduleNodeLocalName = localNameOf(moduleNode);
                            if (moduleNodeLocalName.equals("web")) {
                                String webUri = childNodeText(moduleNode, "web-uri");
                                String contextRoot = childNodeText(moduleNode, "context-root");
                                module = new DefaultEarWebModule(webUri, contextRoot);
                                addModule(module, "web");
                            } else if (moduleNodeLocalName.equals("alt-dd")) {
                                assert module != null;
                                module.setAltDeployDescriptor(moduleNode.text());
                            } else {
                                module = new DefaultEarModule(moduleNode.text());
                                addModule(module, moduleNodeLocalName);
                            }
                        }

                        break;
                    case "security-role":

                        String roleName = childNodeText(child, "role-name");
                        String description = childNodeText(child, "description");
                        addSecurityRole(new DefaultEarSecurityRole(roleName, description));

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
        } catch (IOException | SAXException ex) {
            throw UncheckedException.throwAsUncheckedException(ex);
        } finally {
            IoActions.closeQuietly(reader);
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
        transformer.transform(toXmlNode(Collections.emptySet(), null), fileResolver.resolve(path));
        return this;
    }

    @Override
    public DefaultDeploymentDescriptor writeTo(Writer writer) {
        return writeTo(writer, Collections.emptySet(), null);
    }

    /**
     * Writes the descriptor with additions that apply to one write only, so the stored values are not changed.
     *
     * @param additionalModules modules written after the modules of this descriptor
     * @param defaultLibraryDirectory the library directory written when this descriptor has none
     */
    public DefaultDeploymentDescriptor writeTo(Writer writer, Collection<EarModule> additionalModules, @Nullable String defaultLibraryDirectory) {
        transformer.transform(toXmlNode(additionalModules, defaultLibraryDirectory), writer);
        return this;
    }

    private DomNode toXmlNode(Collection<EarModule> additionalModules, @Nullable String defaultLibraryDirectory) {
        String version = getVersion();
        String applicationName = getApplicationName();
        String description = getDescription();
        String displayName = getDisplayName();
        Boolean initializeInOrder = getInitializeInOrder();
        String libraryDirectory = getLibraryDirectory() != null ? getLibraryDirectory() : defaultLibraryDirectory;
        Set<EarModule> modules = new LinkedHashSet<>(lazyProperties.getModules().getOrElse(Collections.emptySet()));
        modules.addAll(additionalModules);
        Set<EarSecurityRole> securityRoles = lazyProperties.getSecurityRoles().getOrElse(Collections.emptySet());
        Map<String, String> moduleTypeMappings = lazyProperties.getModuleTypeMappings().getOrElse(Collections.emptyMap());

        DomNode root = new DomNode(nodeNameFor("application", version));
        Map<String, String> rootAttributes = Cast.uncheckedCast(root.attributes());
        if (version != null) {
            rootAttributes.put("version", version);
        }
        if (!"1.3".equals(version)) {
            rootAttributes.put("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        }
        if ("1.3".equals(version)) {
            root.setPublicId("-//Sun Microsystems, Inc.//DTD J2EE Application 1.3//EN");
            root.setSystemId("http://java.sun.com/dtd/application_1_3.dtd");
        } else if ("1.4".equals(version)) {
            rootAttributes.put("xsi:schemaLocation", "http://java.sun.com/xml/ns/j2ee http://java.sun.com/xml/ns/j2ee/application_1_4.xsd");
        } else if ("5".equals(version) || "6".equals(version)) {
            rootAttributes.put("xsi:schemaLocation", "http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/application_" + version + ".xsd");
        } else if ("7".equals(version) || "8".equals(version)) {
            rootAttributes.put("xsi:schemaLocation", "http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/application_" + version + ".xsd");
        } else if (version != null && JAKARTA_VERSION_PATTERN.matcher(version).matches()) {
            rootAttributes.put("xsi:schemaLocation", "https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/application_" + version + ".xsd");
        }
        if (applicationName != null) {
            new Node(root, nodeNameFor("application-name", version), applicationName);
        }
        if (description != null) {
            new Node(root, nodeNameFor("description", version), description);
        }
        if (displayName != null) {
            new Node(root, nodeNameFor("display-name", version), displayName);
        }
        if (initializeInOrder != null && initializeInOrder) {
            new Node(root, nodeNameFor("initialize-in-order", version), initializeInOrder);
        }
        for (EarModule module : modules) {
            Node moduleNode = new Node(root, nodeNameFor("module", version));
            module.toXmlNode(moduleNode, moduleNameFor(module, moduleTypeMappings, version));
        }
        for (EarSecurityRole role : securityRoles) {
            Node roleNode = new Node(root, nodeNameFor("security-role", version));
            if (role.getDescription() != null) {
                new Node(roleNode, nodeNameFor("description", version), role.getDescription());
            }
            new Node(roleNode, nodeNameFor("role-name", version), role.getRoleName());
        }
        if (libraryDirectory != null) {
            new Node(root, nodeNameFor("library-directory", version), libraryDirectory);
        }
        return root;
    }

    private static Object moduleNameFor(EarModule module, Map<String, String> moduleTypeMappings, @Nullable String version) {
        String name = moduleTypeMappings.get(module.getPath());
        if (name == null) {
            if (module instanceof EarWebModule) {
                name = "web";
            } else {
                // assume EJB is the most common kind of EAR deployment
                name = "ejb";
            }
        }
        return nodeNameFor(name, version);
    }

    private static Object nodeNameFor(String name, @Nullable String version) {
        if ("1.3".equals(version)) {
            return name;
        } else if ("1.4".equals(version)) {
            return new QName("http://java.sun.com/xml/ns/j2ee", name);
        } else if ("5".equals(version) || "6".equals(version)) {
            return new QName("http://java.sun.com/xml/ns/javaee", name);
        } else if ("7".equals(version) || "8".equals(version)) {
            return new QName("http://xmlns.jcp.org/xml/ns/javaee", name);
        } else if (version != null && JAKARTA_VERSION_PATTERN.matcher(version).matches()) {
            return new QName("https://jakarta.ee/xml/ns/jakartaee", name);
        } else {
            return new QName(name);
        }
    }

    // For tests
    XmlTransformer getTransformer() {
        return transformer;
    }
}
