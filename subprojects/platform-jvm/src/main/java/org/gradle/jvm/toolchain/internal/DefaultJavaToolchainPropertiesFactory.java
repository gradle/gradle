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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaCompilerProperty;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaLauncherProperty;
import org.gradle.jvm.toolchain.JavaToolchainPropertiesFactory;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JavadocTool;
import org.gradle.jvm.toolchain.JavadocToolProperty;

import javax.inject.Inject;

public class DefaultJavaToolchainPropertiesFactory implements JavaToolchainPropertiesFactory {

    private final JavaToolchainQueryService toolchainQueryService;
    private final ObjectFactory objectFactory;
    private JavaToolchainSpec defaultToolchain;

    @Inject
    public DefaultJavaToolchainPropertiesFactory(JavaToolchainQueryService toolchainQueryService, ObjectFactory objectFactory) {
        this.toolchainQueryService = toolchainQueryService;
        this.objectFactory = objectFactory;
    }

    @Override
    public JavaCompilerProperty newJavaCompilerProperty() {
        Property<JavaCompiler> javaCompilerProperty = objectFactory.property(JavaCompiler.class);
        if (defaultToolchain != null) {
            javaCompilerProperty.convention(toolchainQueryService.findMatchingToolchain(defaultToolchain).map(JavaToolchain::getJavaCompiler));
        }
        javaCompilerProperty.finalizeValueOnRead();
        return objectFactory.newInstance(DefaultJavaCompilerProperty.class, javaCompilerProperty, toolchainQueryService);
    }

    @Override
    public JavaLauncherProperty newJavaLauncherProperty() {
        Property<JavaLauncher> javaLauncherProperty = objectFactory.property(JavaLauncher.class);
        if (defaultToolchain != null) {
            javaLauncherProperty.convention(toolchainQueryService.findMatchingToolchain(defaultToolchain).map(JavaToolchain::getJavaLauncher));
        }
        javaLauncherProperty.finalizeValueOnRead();
        return objectFactory.newInstance(DefaultJavaLauncherProperty.class, javaLauncherProperty, toolchainQueryService);
    }

    @Override
    public JavadocToolProperty newJavadocToolProperty() {
        Property<JavadocTool> javadocToolProperty = objectFactory.property(JavadocTool.class);
        if (defaultToolchain != null) {
            javadocToolProperty.convention(toolchainQueryService.findMatchingToolchain(defaultToolchain).map(JavaToolchain::getJavadocTool));
        }
        javadocToolProperty.finalizeValueOnRead();
        return objectFactory.newInstance(DefaultJavadocToolProperty.class, javadocToolProperty, toolchainQueryService);
    }

    public void setDefaultToolchain(JavaToolchainSpec defaultToolchain) {
        this.defaultToolchain = defaultToolchain;
    }
}
