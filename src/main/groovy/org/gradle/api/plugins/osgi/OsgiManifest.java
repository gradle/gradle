/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.api.tasks.bundling.GradleManifest;

import java.util.jar.Manifest;
import java.util.List;
import java.util.Map;
import java.io.File;

/**
 * @author Hans Dockter
 */
public interface OsgiManifest {
    Manifest generateManifest();

    List<String> instructionValue(String instructionName);

    OsgiManifest instruction(String name, String... values);

    OsgiManifest instructionFirst(String name, String... values);

    Map<String, List<String>> getInstructions();

    String getSymbolicName();

    void setSymbolicName(String symbolicName);

    String getName();

    void setName(String name);

    String getVersion();

    void setVersion(String version);

    String getDescription();

    void setDescription(String description);

    String getLicense();

    void setLicense(String license);

    String getVendor();

    void setVendor(String vendor);

    String getDocURL();

    void setDocURL(String docURL);

    File getClassesDir();

    void setClassesDir(File classesDir);

    OsgiManifest overwrite(GradleManifest manifest);

    List<File> getClasspath();

    void setClasspath(List<File> classpath);
}
