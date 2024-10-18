/*
 * Copyright 2024 the original author or authors.
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

import groovy.namespace.QName;
import groovy.util.Node;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.DomNode;
import org.gradle.internal.Cast;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ear.descriptor.DeploymentDescriptor;
import org.gradle.plugins.ear.descriptor.EarModule;
import org.gradle.plugins.ear.descriptor.EarSecurityRole;
import org.gradle.plugins.ear.descriptor.EarWebModule;

import java.io.File;
import java.io.Writer;
import java.util.Map;
import java.util.Set;

@NonNullApi
public class DeploymentDescriptorXmlWriter {

    public static void writeDeploymentDescriptor(DeploymentDescriptor deploymentDescriptor, XmlTransformer transformer, File destination) {
        transformer.transform(toXmlNode(deploymentDescriptor, deploymentDescriptor.getModules().get()), destination);
    }

    public static void writeDeploymentDescriptor(DeploymentDescriptor deploymentDescriptor, XmlTransformer transformer, Writer writer) {
        writeDeploymentDescriptor(deploymentDescriptor, deploymentDescriptor.getModules().get(), transformer, writer);
    }

    public static void writeDeploymentDescriptor(DeploymentDescriptor deploymentDescriptor, Set<EarModule> modules, XmlTransformer transformer, Writer writer) {
        transformer.transform(toXmlNode(deploymentDescriptor, modules), writer);
    }

    private static DomNode toXmlNode(DeploymentDescriptor deploymentDescriptor, Set<EarModule> modules) {
        DomNode root = new DomNode(nodeNameFor(deploymentDescriptor, "application"));
        Map<String, String> rootAttributes = Cast.uncheckedCast(root.attributes());
        String version = deploymentDescriptor.getVersion().get();
        rootAttributes.put("version", version);
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
        } else if ("9".equals(version) || "10".equals(version)) {
            rootAttributes.put("xsi:schemaLocation", "https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/application_" + version + ".xsd");
        }
        if (deploymentDescriptor.getApplicationName().isPresent()) {
            new Node(root, nodeNameFor(deploymentDescriptor, "application-name"), deploymentDescriptor.getApplicationName().get());
        }
        if (deploymentDescriptor.getDescription().isPresent()) {
            new Node(root, nodeNameFor(deploymentDescriptor, "description"), deploymentDescriptor.getDescription().get());
        }
        if (deploymentDescriptor.getDisplayName().isPresent()) {
            new Node(root, nodeNameFor(deploymentDescriptor, "display-name"), deploymentDescriptor.getDisplayName().get());
        }
        if (deploymentDescriptor.getInitializeInOrder().isPresent() && deploymentDescriptor.getInitializeInOrder().get()) {
            new Node(root, nodeNameFor(deploymentDescriptor, "initialize-in-order"), deploymentDescriptor.getInitializeInOrder().get());
        }
        for (EarModule module : modules) {
            Node moduleNode = new Node(root, nodeNameFor(deploymentDescriptor, "module"));
            module.toXmlNode(moduleNode, moduleNameFor(deploymentDescriptor, module));
        }
        for (EarSecurityRole role : deploymentDescriptor.getSecurityRoles().get()) {
            Node roleNode = new Node(root, nodeNameFor(deploymentDescriptor, "security-role"));
            if (role.getDescription().isPresent()) {
                new Node(roleNode, nodeNameFor(deploymentDescriptor, "description"), role.getDescription().get());
            }
            new Node(roleNode, nodeNameFor(deploymentDescriptor, "role-name"), role.getRoleName().get());
        }
        if (deploymentDescriptor.getLibraryDirectory().isPresent()) {
            new Node(root, nodeNameFor(deploymentDescriptor, "library-directory"), deploymentDescriptor.getLibraryDirectory().get());
        }
        return root;
    }

    private static Object nodeNameFor(DeploymentDescriptor deploymentDescriptor, String name) {
        String version = deploymentDescriptor.getVersion().getOrNull();
        if ("1.3".equals(version)) {
            return name;
        } else if ("1.4".equals(version)) {
            return new QName("http://java.sun.com/xml/ns/j2ee", name);
        } else if ("5".equals(version) || "6".equals(version)) {
            return new QName("http://java.sun.com/xml/ns/javaee", name);
        } else if ("7".equals(version) || "8".equals(version)) {
            return new QName("http://xmlns.jcp.org/xml/ns/javaee", name);
        } else if ("9".equals(version) || "10".equals(version)) {
            return new QName("https://jakarta.ee/xml/ns/jakartaee", name);
        } else {
            return new QName(name);
        }
    }

    private static Object moduleNameFor(DeploymentDescriptor deploymentDescriptor, EarModule module) {
        String name = deploymentDescriptor.getModuleTypeMappings().get().get(module.getPath().get());
        if (name == null) {
            if (module instanceof EarWebModule) {
                name = "web";
            } else {
                // assume EJB is the most common kind of EAR deployment
                name = "ejb";
            }
        }
        return nodeNameFor(deploymentDescriptor, name);
    }
}
