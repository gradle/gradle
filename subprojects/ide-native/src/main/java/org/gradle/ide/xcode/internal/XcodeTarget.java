/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.ide.xcode.internal;

import org.gradle.api.Named;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.provider.Provider;
import org.gradle.ide.xcode.internal.xcodeproj.PBXTarget;

import javax.inject.Inject;

/**
 * @see <a href="https://developer.apple.com/library/content/featuredarticles/XcodeConcepts/Concept-Schemes.html">XCode Scheme Concept</a>
 */
public class XcodeTarget implements Named {
    private final String id;
    private final String name;
    private final ConfigurableFileCollection headerSearchPaths;
    private final ConfigurableFileCollection compileModules;
    private final ConfigurableFileCollection sources;
    private String taskName;
    private String gradleCommand;

    private final Provider<FileSystemLocation> debugOutputFile;
    private final Provider<FileSystemLocation> releaseOutputFile;
    private PBXTarget.ProductType productType;
    private String productName;
    private String outputFileType;

    @Inject
    public XcodeTarget(String name, String id, FileOperations fileOperations, Provider<FileSystemLocation> debugOutputFile, Provider<FileSystemLocation> releaseOutputFile) {
        this.name = name;
        this.id = id;
        this.sources = fileOperations.files();
        this.headerSearchPaths = fileOperations.files();
        this.compileModules = fileOperations.files();
        this.debugOutputFile = debugOutputFile;
        this.releaseOutputFile = releaseOutputFile;
    }

    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    public Provider<FileSystemLocation> getDebugOutputFile() {
        return debugOutputFile;
    }

    public Provider<FileSystemLocation> getReleaseOutputFile() {
        return releaseOutputFile;
    }

    public String getOutputFileType() {
        return outputFileType;
    }

    public void setOutputFileType(String outputFileType) {
        this.outputFileType = outputFileType;
    }

    public PBXTarget.ProductType getProductType() {
        return productType;
    }

    public void setProductType(PBXTarget.ProductType productType) {
        this.productType = productType;
    }

    public boolean isRunnable() {
        return PBXTarget.ProductType.TOOL.equals(getProductType());
    }

    public boolean isUnitTest() {
        return PBXTarget.ProductType.UNIT_TEST.equals(getProductType());
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getGradleCommand() {
        return gradleCommand;
    }

    public void setGradleCommand(String gradleCommand) {
        this.gradleCommand = gradleCommand;
    }

    public ConfigurableFileCollection getSources() {
        return sources;
    }

    public ConfigurableFileCollection getHeaderSearchPaths() {
        return headerSearchPaths;
    }

    public ConfigurableFileCollection getCompileModules() {
        return compileModules;
    }
}
