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
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.ide.xcode.internal.xcodeproj.PBXTarget;

import java.io.File;

/**
 * @see <a href="https://developer.apple.com/library/content/featuredarticles/XcodeConcepts/Concept-Schemes.html">XCode Scheme Concept</a>
 */
public class XcodeTarget implements Named {
    private final String id;
    private final String name;
    private final ConfigurableFileCollection headerSearchPaths;
    private final ConfigurableFileCollection importPaths;
    private FileCollection sources;
    private String taskName;
    private String gradleCommand;

    private File outputFile;
    private PBXTarget.ProductType productType;
    private String productName;
    private String outputFileType;

    public XcodeTarget(String name, String id, FileOperations fileOperations) {
        this.name = name;
        this.id = id;
        this.headerSearchPaths = fileOperations.files();
        this.importPaths = fileOperations.files();
    }

    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
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

    public FileCollection getSources() {
        return sources;
    }

    public void setSources(FileCollection sources) {
        this.sources = sources;
    }

    public ConfigurableFileCollection getHeaderSearchPaths() {
        return headerSearchPaths;
    }

    public ConfigurableFileCollection getImportPaths() {
        return importPaths;
    }
}
