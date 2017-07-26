/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.plugins.osgi;

import org.gradle.api.file.FileCollection;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Represents a manifest file for a JAR containing an OSGi bundle.
 */
public interface OsgiManifest extends org.gradle.api.java.archives.Manifest {
    /**
     * Returns the list of arguments for a particular instruction.
     *
     * @return The list of arguments
     * @see #instruction(String, String...)
     */
    List<String> instructionValue(String instructionName);

    /**
     * Adds arguments to an instruction. If the instruction does not exists, it is created. If it does exists, the
     * arguments are appended to the existing arguments.
     *
     * @return this
     * @see #instructionFirst(String, String...)
     * @see #instructionReplace(String, String...)
     */
    OsgiManifest instruction(String name, String... values);

    /**
     * Adds arguments to an instruction. If the instruction does not exists, it is created. If it does exists, the
     * arguments are inserted before the existing arguments.
     *
     * @param name Name of the instruction.
     * @param values The values for the instruction.
     * @return this
     * @see #instruction(String, String...)
     * @see #instructionReplace(String, String...)
     */
    OsgiManifest instructionFirst(String name, String... values);

    /**
     * Sets the values for an instruction. If the instruction does not exists, it is created. If it does exists, the
     * values replace the existing values.
     *
     * @param name Name of the instruction.
     * @param values The values for the instruction.
     * @return this
     * @see #instruction(String, String...)
     * @see #instructionFirst(String, String...)
     */
    OsgiManifest instructionReplace(String name, String... values);

    /**
     * Returns all existing instruction.
     *
     * @return A map with instructions. The key of the map is the instruction name, the value a list of arguments.
     */
    Map<String, List<String>> getInstructions();

    /**
     * Returns the symbolic name.
     *
     * @see #setSymbolicName(String)
     * @return the symbolic name.
     */
    String getSymbolicName();

    /**
     * A convenient method for setting a Bundle-SymbolicName instruction.
     *
     * @param symbolicName the symbolicName to set
     */
    void setSymbolicName(String symbolicName);

    /**
     * Returns the name.
     *
     * @see #setName(String)
     */
    String getName();

    /**
     * A convenient method for setting a Bundle-Name instruction.
     *
     * @param name the name to set
     */
    void setName(String name);

    /**
     * Returns the version.
     *
     * @see #setVersion(String)
     */
    String getVersion();

    /**
     * A convenient method for setting a Bundle-Version instruction.
     *
     * @param version the version to set
     */
    void setVersion(String version);

    /**
     * Returns the description.
     *
     * @see #setDescription(String)
     */
    String getDescription();

    /**
     * A convenient method for setting a Bundle-Description instruction.
     *
     * @param description the description to set
     */
    void setDescription(String description);

    /**
     * Returns the license.
     * @see #setLicense(String)
     */
    String getLicense();

    /**
     * A convenient method for setting a Bundle-License instruction.
     *
     * @param license The license to set
     */
    void setLicense(String license);

    /**
     * Returns the vendor.
     *
     * @see #setVendor(String)
     */
    String getVendor();

    /**
     * A convenient method for setting a Bundle-Vendor instruction.
     *
     * @param vendor The vendor to set
     */
    void setVendor(String vendor);

    /**
     * Returns the docURL value.
     *
     * @see #setDocURL(String)
     */
    String getDocURL();

    /**
     * A convenient method for setting a Bundle-DocURL instruction.
     *
     * @param docURL the docURL to set.
     */
    void setDocURL(String docURL);

    /**
     * Returns the classes dir.
     *
     * @see #setClassesDir(java.io.File)
     */
    File getClassesDir();

    /**
     * Sets the classes dir. This directory is the major source of input for generation the OSGi manifest. All classes
     * are analyzed for its packages and package dependencies. Based on this the Import-Package value is set.
     * This auto generated value can be overwritten by explicitly setting an instruction.
     *
     * @see #instruction(String, String...)
     */
    void setClassesDir(File classesDir);

    /**
     * Returns the classpath.
     *
     * @see #setClasspath(org.gradle.api.file.FileCollection)
     */
    FileCollection getClasspath();

    /**
     * A convenient method for setting a Bundle-Classpath instruction. The information of the classpath elements are only
     * used if they are OSGi bundles. In this case for example the version information provided by the bundle is used in the Import-Package of the generated
     * OSGi bundle.
     *
     * @param classpath The classpath elements
     */
    void setClasspath(FileCollection classpath);
}
