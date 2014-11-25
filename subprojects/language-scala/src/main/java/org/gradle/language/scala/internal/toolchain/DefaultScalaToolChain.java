/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.scala.internal.toolchain;

import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.scala.ScalaBasePlugin;
import org.gradle.language.scala.platform.ScalaPlatform;
import org.gradle.language.scala.toolchain.ScalaToolChain;

public class DefaultScalaToolChain implements ScalaToolChain {
    private final ConfigurationContainer configurationContainer;
    private final DependencyHandler dependencyHandler;
    private final JavaVersion javaVersion;

    public DefaultScalaToolChain(ConfigurationContainer configurationContainer, DependencyHandler dependencyHandler) {
        this.configurationContainer = configurationContainer;
        this.dependencyHandler = dependencyHandler;
        this.javaVersion = JavaVersion.current();
    }

    public FileCollection getScalaClasspath(ScalaPlatform platform) {
        Dependency scalaDependency = dependencyHandler.create(String.format("org.scala-lang:scala-compiler:%s", platform.getScalaVersion()));
        Configuration scalaCompileConfiguration = configurationContainer.detachedConfiguration(scalaDependency);
        return scalaCompileConfiguration;
    }

    public FileCollection getZincClasspath() {
        Dependency zincDependency = dependencyHandler.create(String.format("com.typesafe.zinc:zinc:%s", ScalaBasePlugin.DEFAULT_ZINC_VERSION));
        Configuration scalaCompileConfiguration = configurationContainer.detachedConfiguration(zincDependency);
        return scalaCompileConfiguration;
    }

    public String getName() {
        return String.format("Scala Toolchain");
    }

    public String getDisplayName() {
        return String.format("Scala Toolchain (JDK %s (%s))", javaVersion.getMajorVersion(), javaVersion);
    }

}
