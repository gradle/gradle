/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling.idea;

import com.google.common.collect.ImmutableList;
import org.gradle.api.JavaVersion;
import org.gradle.api.NonNullApi;
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;

/**
 * Represents an IDEA module in isolation.
 * <p>
 * <b>This model is internal, and is NOT part of the public Tooling API.</b>
 */
@NonNullApi
public class IsolatedIdeaModuleInternal implements Serializable {

    private String name;
    private @Nullable JavaVersion javaSourceCompatibility;
    private @Nullable JavaVersion javaTargetCompatibility;
    private @Nullable IdeaLanguageLevel explicitSourceLanguageLevel;
    private @Nullable JavaVersion explicitTargetBytecodeVersion;
    private DefaultIdeaContentRoot contentRoot;
    private String jdkName;
    private DefaultIdeaCompilerOutput compilerOutput;
    private List<DefaultIdeaDependency> dependencies = ImmutableList.of();

    public String toString() {
        return "IsolatedIdeaModuleInternal{"
            + "contentRoot='" + contentRoot.getRootDirectory() + '\''
            + '}';
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Nullable
    public JavaVersion getJavaSourceCompatibility() {
        return javaSourceCompatibility;
    }

    public void setJavaSourceCompatibility(@Nullable JavaVersion javaSourceCompatibility) {
        this.javaSourceCompatibility = javaSourceCompatibility;
    }

    @Nullable
    public JavaVersion getJavaTargetCompatibility() {
        return javaTargetCompatibility;
    }

    public void setJavaTargetCompatibility(@Nullable JavaVersion javaTargetCompatibility) {
        this.javaTargetCompatibility = javaTargetCompatibility;
    }

    public DefaultIdeaContentRoot getContentRoot() {
        return contentRoot;
    }

    public void setContentRoot(DefaultIdeaContentRoot contentRoot) {
        this.contentRoot = contentRoot;
    }

    public String getJdkName() {
        return jdkName;
    }

    public void setJdkName(String jdkName) {
        this.jdkName = jdkName;
    }

    public DefaultIdeaCompilerOutput getCompilerOutput() {
        return compilerOutput;
    }

    public void setCompilerOutput(DefaultIdeaCompilerOutput compilerOutput) {
        this.compilerOutput = compilerOutput;
    }

    @Nullable
    public IdeaLanguageLevel getExplicitSourceLanguageLevel() {
        return explicitSourceLanguageLevel;
    }

    public void setExplicitSourceLanguageLevel(@Nullable IdeaLanguageLevel explicitSourceLanguageLevel) {
        this.explicitSourceLanguageLevel = explicitSourceLanguageLevel;
    }

    @Nullable
    public JavaVersion getExplicitTargetBytecodeVersion() {
        return explicitTargetBytecodeVersion;
    }

    public void setExplicitTargetBytecodeVersion(@Nullable JavaVersion explicitTargetBytecodeVersion) {
        this.explicitTargetBytecodeVersion = explicitTargetBytecodeVersion;
    }

    public List<DefaultIdeaDependency> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<DefaultIdeaDependency> dependencies) {
        this.dependencies = ImmutableList.copyOf(dependencies); // also ensures it's serializable
    }
}
