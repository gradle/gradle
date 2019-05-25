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

import com.google.common.collect.Lists;
import org.gradle.api.Named;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.ide.xcode.internal.xcodeproj.FileTypes;
import org.gradle.ide.xcode.internal.xcodeproj.PBXTarget;
import org.gradle.language.swift.SwiftVersion;

import javax.inject.Inject;
import java.util.List;

import static org.gradle.ide.xcode.internal.DefaultXcodeProject.BUILD_DEBUG;

/**
 * @see <a href="https://developer.apple.com/library/content/featuredarticles/XcodeConcepts/Concept-Schemes.html">XCode Scheme Concept</a>
 */
public class XcodeTarget implements Named {
    private final String id;
    private final String name;
    private final ConfigurableFileCollection headerSearchPaths;
    private final ConfigurableFileCollection compileModules;
    private final ConfigurableFileCollection sources;
    private final List<TaskDependency> taskDependencies = Lists.newArrayList();
    private String taskName;
    private String gradleCommand;

    private List<XcodeBinary> binaries = Lists.newArrayList();
    private Provider<? extends FileSystemLocation> debugOutputFile;
    private PBXTarget.ProductType productType;
    private String productName;
    private Property<SwiftVersion> swiftSourceCompatibility;
    private Property<String> defaultConfigurationName;

    @Inject
    public XcodeTarget(String name, String id, ObjectFactory objectFactory) {
        this.name = name;
        this.id = id;
        this.sources = objectFactory.fileCollection();
        this.headerSearchPaths = objectFactory.fileCollection();
        this.compileModules = objectFactory.fileCollection();
        this.swiftSourceCompatibility = objectFactory.property(SwiftVersion.class);
        this.defaultConfigurationName = objectFactory.property(String.class);
        this.defaultConfigurationName.set(BUILD_DEBUG);
        this.debugOutputFile = Providers.notDefined();
    }

    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    public Provider<? extends FileSystemLocation> getDebugOutputFile() {
        return debugOutputFile;
    }

    public String getOutputFileType() {
        return toFileType(productType);
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

    public void addTaskDependency(TaskDependency taskDependency) {
        taskDependencies.add(taskDependency);
    }

    public List<TaskDependency> getTaskDependencies() {
        return taskDependencies;
    }

    public List<XcodeBinary> getBinaries() {
        return binaries;
    }

    private static String toFileType(PBXTarget.ProductType productType) {
        if (PBXTarget.ProductType.TOOL.equals(productType)) {
            return FileTypes.MACH_O_EXECUTABLE.identifier;
        } else if (PBXTarget.ProductType.DYNAMIC_LIBRARY.equals(productType)) {
            return FileTypes.MACH_O_DYNAMIC_LIBRARY.identifier;
        } else if (PBXTarget.ProductType.STATIC_LIBRARY.equals(productType)) {
            return FileTypes.ARCHIVE_LIBRARY.identifier;
        } else {
            return "compiled";
        }
    }

    public Property<SwiftVersion> getSwiftSourceCompatibility() {
        return swiftSourceCompatibility;
    }

    public void addBinary(String configuration, Provider<? extends FileSystemLocation> outputFile, String architectureName) {
        binaries.add(new XcodeBinary(configuration, outputFile, architectureName));
        if (configuration.contains("Debug")) {
            this.debugOutputFile = outputFile;
        }
    }

    public Property<String> getDefaultConfigurationName() {
        return defaultConfigurationName;
    }

    public boolean isBuildable() {
         return !binaries.isEmpty();
    }
}
