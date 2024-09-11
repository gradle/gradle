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
package org.gradle.plugins.ear.descriptor;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.XmlProvider;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;

import java.io.Reader;
import java.io.Writer;

/**
 * A deployment descriptor such as application.xml.
 */
public interface DeploymentDescriptor {

    /**
     * The name of the descriptor file, typically "application.xml"
     */
    @ToBeReplacedByLazyProperty
    String getFileName();

    void setFileName(String fileName);

    /**
     * The version of application.xml. Required. Valid versions are "1.3", "1.4", "5", "6", "7", "8", "9" and "10". Defaults to "6".
     */
    @ReplacesEagerProperty
    Property<String> getVersion();

    /**
     * The application name. Optional. Only valid with version 6.
     */
    @ReplacesEagerProperty
    Property<String> getApplicationName();

    /**
     * Whether to initialize modules in the order they appear in the descriptor, with the exception of client modules.
     * Optional. Only valid with version 6.
     */
    @ReplacesEagerProperty
    Property<Boolean> getInitializeInOrder();

    /**
     * The application description. Optional.
     */
    @ReplacesEagerProperty
    Property<String> getDescription();

    /**
     * The application display name. Optional.
     */
    @ReplacesEagerProperty
    Property<String> getDisplayName();

    /**
     * The name of the directory to look for libraries in. Optional. If not specified, {@link org.gradle.plugins.ear.Ear#getLibDirName()} is used.
     * Typically, this should be set via {@link org.gradle.plugins.ear.EarPluginConvention#setLibDirName(String)} instead of this property
     * when using the <code>ear</code> plugin.
     */
    @ReplacesEagerProperty
    Property<String> getLibraryDirectory();

    /**
     * List of module descriptors. Must not be empty. Non-null and order-maintaining by default. Must maintain order if
     * initializeInOrder is <code>true</code>.
     */
    @ReplacesEagerProperty
    SetProperty<EarModule> getModules();

    /**
     * Add a module to the deployment descriptor.
     *
     * @param module
     *            The module to add.
     * @param type
     *            The type of the module, such as "ejb", "java", etc.
     * @return this.
     */
    DeploymentDescriptor module(EarModule module, String type);

    /**
     * Add a module to the deployment descriptor.
     *
     * @param path
     *            The path of the module to add.
     * @param type
     *            The type of the module, such as "ejb", "java", etc.
     * @return this.
     */
    DeploymentDescriptor module(String path, String type);

    /**
     * Add a web module to the deployment descriptor.
     *
     * @param path
     *            The path of the module to add.
     * @param contextRoot
     *            The context root type of the web module.
     * @return this.
     */
    DeploymentDescriptor webModule(String path, String contextRoot);

    /**
     * List of security roles. Optional. Non-null and order-maintaining by default.
     */
    @ReplacesEagerProperty
    SetProperty<EarSecurityRole> getSecurityRoles();

    /**
     * Add a security role to the deployment descriptor.
     *
     * @param role
     *            The security role to add.
     * @return this.
     */
    DeploymentDescriptor securityRole(EarSecurityRole role);

    /**
     * Add a security role to the deployment descriptor.
     *
     * @param role
     *            The name of the security role to add.
     * @return this.
     */
    DeploymentDescriptor securityRole(String role);

    /**
     * Add a security role to the deployment descriptor after configuring it with the given action.
     *
     * @param action an action to configure the security role
     * @return this.
     */
    DeploymentDescriptor securityRole(Action<? super EarSecurityRole> action);

    /**
     * Mapping of module paths to module types. Non-null by default. For example, to specify that a module is a java
     * module, set <code>moduleTypeMappings["myJavaModule.jar"] = "java"</code>.
     */
    @ReplacesEagerProperty
    MapProperty<String, String> getModuleTypeMappings();

    /**
     * Adds a closure to be called when the XML document has been created. The XML is passed to the closure as a
     * parameter in form of a {@link groovy.util.Node}. The closure can modify the XML before it is written to the
     * output file. This allows additional JavaEE version 6 elements like "data-source" or "resource-ref" to be
     * included.
     *
     * @param closure
     *            The closure to execute when the XML has been created
     * @return this
     */
    DeploymentDescriptor withXml(@DelegatesTo(XmlProvider.class) Closure closure);

    /**
     * Adds an action to be called when the XML document has been created. The XML is passed to the action as a
     * parameter in form of a {@link groovy.util.Node}. The action can modify the XML before it is written to the output
     * file. This allows additional JavaEE version 6 elements like "data-source" or "resource-ref" to be included.
     *
     * @param action
     *            The action to execute when the XML has been created
     * @return this
     */
    DeploymentDescriptor withXml(Action<? super XmlProvider> action);

    /**
     * Reads the deployment descriptor from a reader.
     *
     * @param reader
     *            The reader to read the deployment descriptor from
     * @return this
     */
    DeploymentDescriptor readFrom(Reader reader);

    /**
     * Reads the deployment descriptor from a file. The paths are resolved as defined by
     * {@link org.gradle.api.Project#file(Object)}
     *
     * @param path
     *            The path of the file to read the deployment descriptor from
     * @return whether the descriptor could be read from the given path
     */
    boolean readFrom(Object path);

    /**
     * Writes the deployment descriptor into a writer.
     *
     * @param writer
     *            The writer to write the deployment descriptor to
     * @return this
     */
    DeploymentDescriptor writeTo(Writer writer);

    /**
     * Writes the deployment descriptor into a file. The paths are resolved as defined by
     * {@link org.gradle.api.Project#file(Object)}
     *
     * @param path
     *            The path of the file to write the deployment descriptor into.
     * @return this
     */
    DeploymentDescriptor writeTo(Object path);
}
