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
package org.gradle.plugins.ear.descriptor.internal

import groovy.xml.QName
import org.gradle.api.Action
import org.gradle.api.UncheckedIOException
import org.gradle.api.XmlProvider
import org.gradle.api.internal.DomNode
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.xml.XmlTransformer
import org.gradle.plugins.ear.descriptor.DeploymentDescriptor
import org.gradle.plugins.ear.descriptor.EarModule
import org.gradle.plugins.ear.descriptor.EarSecurityRole
import org.gradle.plugins.ear.descriptor.EarWebModule

class DefaultDeploymentDescriptor implements DeploymentDescriptor {

    private String fileName = "application.xml"
    String version = "6"
    String applicationName
    Boolean initializeInOrder
    String description
    String displayName
    String libraryDirectory
    Set<? extends EarModule> modules = new LinkedHashSet<EarModule>()
    Set<? extends EarSecurityRole> securityRoles = new LinkedHashSet<EarSecurityRole>()
    Map<String, String> moduleTypeMappings = new HashMap<String, String>()
    private FileResolver fileResolver
    final XmlTransformer transformer = new XmlTransformer()

    public DefaultDeploymentDescriptor(FileResolver fileResolver) {
        this(new File("META-INF", "application.xml"), fileResolver)
    }

    public DefaultDeploymentDescriptor(Object descriptorPath, FileResolver fileResolver) {
        this.fileResolver = fileResolver
        if (fileResolver) {
            File descriptorFile = fileResolver.resolve(descriptorPath)
            if (descriptorFile) {
                fileName = descriptorFile.name
                readFrom descriptorFile
            }
        }
    }

    public String getFileName() {
        return fileName
    }
    public void setFileName(String fileName) {
        this.fileName = fileName
        readFrom new File("META-INF", fileName)
	}

    public DefaultDeploymentDescriptor module(EarModule module, String type) {
        modules.add module
        moduleTypeMappings[module.path] = type
        return this
    }

    public DefaultDeploymentDescriptor module(String path, String type) {
        return module(new DefaultEarModule(path), type)
    }

    public DefaultDeploymentDescriptor webModule(String path, String contextRoot) {
        modules.add(new DefaultEarWebModule(path, contextRoot))
        moduleTypeMappings[path] = "web"
        return this
    }

    public DefaultDeploymentDescriptor securityRole(EarSecurityRole role) {
        securityRoles.add role
        return this
    }

    public DeploymentDescriptor securityRole(String role) {
        securityRoles.add(new DefaultEarSecurityRole(role))
        return this
    }

    public DeploymentDescriptor withXml(Closure closure) {
        transformer.addAction(closure)
        return this
    }

    public DeploymentDescriptor withXml(Action<? super XmlProvider> action) {
        transformer.addAction(action)
        return this
    }

    boolean readFrom(Object path) {
        if (!fileResolver) {
            return false
        }
        File descriptorFile = fileResolver.resolve(path)
        if (!descriptorFile || !descriptorFile.exists()) {
            return false
        }

        try {
            FileReader reader = new FileReader(descriptorFile)
            readFrom reader
            return true
        } catch (IOException e) {
            throw new UncheckedIOException(e)
        }
    }

    DeploymentDescriptor readFrom(Reader reader) {
        try {
            def appNode = new XmlParser().parse(reader)
            version = appNode.@version

            appNode.children().each { child ->
                switch (localNameOf(child)) {
                    case "application-name":
                        applicationName = child.text()
                        break
                    case "initialize-in-order":
                        initializeInOrder = child.text() as Boolean
                        break
                    case "description":
                        description = child.text()
                        break
                    case "display-name":
                        displayName = child.text()
                        break
                    case "library-directory":
                        libraryDirectory = child.text()
                        break
                    case "module":
                        def module
                        child.children().each { moduleNode ->
                            switch (localNameOf(moduleNode)) {
                                case "web":
                                    module = new DefaultEarWebModule(moduleNode."web-uri".text(), moduleNode."context-root".text())
                                    modules.add(module)
                                    moduleTypeMappings[module.path] = "web"
                                    break
                                case "alt-dd":
                                    module.altDeployDescriptor = moduleNode.text()
                                    break
                                default:
                                    module = new DefaultEarModule(moduleNode.text())
                                    modules.add(module)
                                    moduleTypeMappings[module.path] = localNameOf(moduleNode)
                                    break
                            }
                        }
                        break
                    case "security-role":
                        securityRoles.add(new DefaultEarSecurityRole(child."role-name".text(), child.description.text()))
                        break
                    default:
                        withXml { it.asNode().append child}
                        break
                }
            }
        } finally {
            reader.close();
        }
        return this
    }

    private String localNameOf(Node node) {
        node.name() instanceof QName ? node.name().localPart : node.name() as String
    }

    public DefaultDeploymentDescriptor writeTo(Object path) {
        transformer.transform(toXmlNode(), fileResolver.resolve(path))
        return this;
    }

    public DefaultDeploymentDescriptor writeTo(Writer writer) {
        transformer.transform(toXmlNode(), writer)
        return this;
    }

    protected Node toXmlNode() {
        DomNode root = new DomNode(nodeNameFor("application"))
        root.@version = version
        if (version != "1.3") {
            root.@"xmlns:xsi" = "http://www.w3.org/2001/XMLSchema-instance"
        }
        switch (version) {
            case "1.3":
                root.publicId = "-//Sun Microsystems, Inc.//DTD J2EE Application 1.3//EN"
                root.systemId = "http://java.sun.com/dtd/application_1_3.dtd"
                break
            case "1.4":
                root.@"xsi:schemaLocation" = "http://java.sun.com/xml/ns/j2ee http://java.sun.com/xml/ns/j2ee/application_1_4.xsd"
                break
            case "5":
            case "6":
                root.@"xsi:schemaLocation" = "http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/application_${version}.xsd"
                break
        }

        if (applicationName) {
            new Node(root, nodeNameFor("application-name"), applicationName)
        }
        if (description) {
            new Node(root, nodeNameFor("description"), description)
        }
        if (displayName) {
            new Node(root, nodeNameFor("display-name"), displayName)
        }
        if (initializeInOrder) {
            new Node(root, nodeNameFor("initialize-in-order"), initializeInOrder)
        }
        modules.each { module ->
            def moduleNode = new Node(root, nodeNameFor("module"))
            module.toXmlNode(moduleNode, moduleNameFor(module))
        }
        if (securityRoles) {
            securityRoles.each { role ->
                def roleNode = new Node(root, nodeNameFor("security-role"))
                if (role.description) {
                    new Node(roleNode, nodeNameFor("description"), role.description)
                }
                new Node(roleNode, nodeNameFor("role-name"), role.roleName)
            }
        }
        if (libraryDirectory) {
            new Node(root, nodeNameFor("library-directory"), libraryDirectory)
        }
        return root
    }

    protected Object moduleNameFor(EarModule module) {

        def name = moduleTypeMappings[module.path]
        if (!name) {
            if (module instanceof EarWebModule) {
                name = "web"
            } else {
                // assume EJB is the most common kind of EAR deployment
                name = "ejb"
            }
        }
        return nodeNameFor(name)
    }

    protected Object nodeNameFor(name) {

        switch (version) {
            case "1.3":
                return name
            case "1.4":
                return new QName("http://java.sun.com/xml/ns/j2ee", name)
            default:
                return new QName("http://java.sun.com/xml/ns/javaee", name)
        }
    }
}
