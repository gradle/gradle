/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain;

import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.file.DirectoryProperty;

/**
 * A {@code JavaInstallationDefinition} represents a logical installation of a Java Runtime Environment (JRE) or a Java Development Kit (JDK).
 *
 * <p>
 * The following example shows how you can configure a local jdk installation, which
 * can be used further to configure tasks consuming tools from that installation.
 *
 * <pre>
 * plugins {
 *     id 'java'
 * }
 *
 * javaInstallations {
 *   jdk13 {
 *     path = "/usr/lib/jvm/java-11.0.7-openjdk"
 *   }
 * }
 * </pre>
 */
// TODO: consider naming this JavaInstallation and rename existing (incubating) JavaInstallation to "LocalJavaInstallation"/hide it altogether
@Incubating
public interface LogicalJavaInstallation extends Named {

    DirectoryProperty getPath();

}
