/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.plugins.internal;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.internal.deprecation.DeprecationLogger;

import java.io.File;

@org.gradle.api.NonNullApi
public class NaggingJavaPluginConvention extends JavaPluginConvention {
    private final DefaultJavaPluginConvention delegate;

    public NaggingJavaPluginConvention(DefaultJavaPluginConvention delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object sourceSets(Closure closure) {
        logDeprecation();
        return delegate.sourceSets(closure);
    }

    @Override
    public File getDocsDir() {
        logDeprecation();
        return delegate.getDocsDir();
    }

    @Override
    public File getTestResultsDir() {
        logDeprecation();
        return delegate.getTestResultsDir();
    }

    @Override
    public File getTestReportDir() {
        logDeprecation();
        return delegate.getTestReportDir();
    }

    @Override
    public JavaVersion getSourceCompatibility() {
        logDeprecation();
        return delegate.getSourceCompatibility();
    }

    @Override
    public void setSourceCompatibility(Object value) {
        logDeprecation();
        delegate.setSourceCompatibility(value);
    }

    @Override
    public void setSourceCompatibility(JavaVersion value) {
        logDeprecation();
        delegate.setSourceCompatibility(value);
    }

    @Override
    public JavaVersion getTargetCompatibility() {
        logDeprecation();
        return delegate.getTargetCompatibility();
    }

    @Override
    public void setTargetCompatibility(Object value) {
        logDeprecation();
        delegate.setTargetCompatibility(value);
    }

    @Override
    public void setTargetCompatibility(JavaVersion value) {
        logDeprecation();
        delegate.setTargetCompatibility(value);
    }

    @Override
    public Manifest manifest() {
        logDeprecation();
        return delegate.manifest();
    }

    @Override
    public Manifest manifest(Closure closure) {
        logDeprecation();
        return delegate.manifest(closure);
    }

    @Override
    public Manifest manifest(Action<? super Manifest> action) {
        logDeprecation();
        return delegate.manifest(action);
    }

    @Override
    public String getDocsDirName() {
        logDeprecation();
        return delegate.getDocsDirName();
    }

    @Override
    public void setDocsDirName(String docsDirName) {
        logDeprecation();
        delegate.setDocsDirName(docsDirName);
    }

    @Override
    public String getTestResultsDirName() {
        logDeprecation();
        return delegate.getTestResultsDirName();
    }

    @Override
    public void setTestResultsDirName(String testResultsDirName) {
        logDeprecation();
        delegate.setTestResultsDirName(testResultsDirName);
    }

    @Override
    public String getTestReportDirName() {
        logDeprecation();
        return delegate.getTestReportDirName();
    }

    @Override
    public void setTestReportDirName(String testReportDirName) {
        logDeprecation();
        delegate.setTestReportDirName(testReportDirName);
    }

    @Override
    public SourceSetContainer getSourceSets() {
        logDeprecation();
        return delegate.getSourceSets();
    }

    @Override
    public ProjectInternal getProject() {
        logDeprecation();
        return delegate.getProject();
    }

    @Override
    public void disableAutoTargetJvm() {
        logDeprecation();
        delegate.disableAutoTargetJvm();
    }

    @Override
    public boolean getAutoTargetJvmDisabled() {
        logDeprecation();
        return delegate.getAutoTargetJvmDisabled();
    }

    File getReportsDir() {
        logDeprecation();
        return delegate.getReportsDir();
    }

    private static void logDeprecation() {
        DeprecationLogger.deprecateType(JavaPluginConvention.class)
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "java_convention_deprecation")
            .nagUser();
    }
}
