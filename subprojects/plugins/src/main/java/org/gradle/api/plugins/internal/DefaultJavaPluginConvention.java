/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.util.internal.RelativePathUtil;

import javax.inject.Inject;
import java.io.File;

import static org.gradle.api.reflect.TypeOf.typeOf;

public abstract class DefaultJavaPluginConvention extends JavaPluginConvention implements HasPublicType {

    private final ProjectInternal project;
    private final JavaPluginExtension extension;

    @Inject
    public DefaultJavaPluginConvention(ProjectInternal project, JavaPluginExtension extension) {
        this.project = project;
        this.extension = extension;
    }

    @Override
    @Deprecated
    public TypeOf<?> getPublicType() {
        emitDeprecationWarning();
        return typeOf(JavaPluginConvention.class);
    }

    @Override
    public Object sourceSets(Closure closure) {
        emitDeprecationWarning();
        return extension.sourceSets(closure);
    }

    @Override
    public File getDocsDir() {
        emitDeprecationWarning();
        return extension.getDocsDir().get().getAsFile();
    }

    @Override
    public File getTestResultsDir() {
        emitDeprecationWarning();
        return extension.getTestResultsDir().get().getAsFile();
    }

    @Override
    public File getTestReportDir() {
        emitDeprecationWarning();
        return extension.getTestReportDir().get().getAsFile();
    }

    @Override
    public JavaVersion getSourceCompatibility() {
        emitDeprecationWarning();
        return extension.getSourceCompatibility();
    }

    @Override
    public void setSourceCompatibility(Object value) {
        emitDeprecationWarning();
        extension.setSourceCompatibility(value);
    }

    @Override
    public void setSourceCompatibility(JavaVersion value) {
        emitDeprecationWarning();
        extension.setSourceCompatibility(value);
    }

    @Override
    public JavaVersion getTargetCompatibility() {
        emitDeprecationWarning();
        return extension.getTargetCompatibility();
    }

    @Override
    public void setTargetCompatibility(Object value) {
        emitDeprecationWarning();
        extension.setTargetCompatibility(value);
    }

    @Override
    public void setTargetCompatibility(JavaVersion value) {
        emitDeprecationWarning();
        extension.setTargetCompatibility(value);
    }

    @Override
    public Manifest manifest() {
        emitDeprecationWarning();
        return extension.manifest();
    }

    @Override
    public Manifest manifest(Closure closure) {
        emitDeprecationWarning();
        return extension.manifest(closure);
    }

    @Override
    public Manifest manifest(Action<? super Manifest> action) {
        emitDeprecationWarning();
        return extension.manifest(action);
    }

    @Override
    public String getDocsDirName() {
        emitDeprecationWarning();
        return relativePath(project.getLayout().getBuildDirectory(), extension.getDocsDir());
    }

    @Override
    public void setDocsDirName(String docsDirName) {
        emitDeprecationWarning();
        extension.getDocsDir().set(project.getLayout().getBuildDirectory().dir(docsDirName));
    }

    @Override
    public String getTestResultsDirName() {
        emitDeprecationWarning();
        return relativePath(project.getLayout().getBuildDirectory(), extension.getTestResultsDir());
    }

    @Override
    public void setTestResultsDirName(String testResultsDirName) {
        emitDeprecationWarning();
        extension.getTestResultsDir().set(project.getLayout().getBuildDirectory().dir(testResultsDirName));
    }

    @Override
    public String getTestReportDirName() {
        emitDeprecationWarning();
        return relativePath(project.getExtensions().getByType(ReportingExtension.class).getBaseDirectory(), extension.getTestReportDir());
    }

    @Override
    public void setTestReportDirName(String testReportDirName) {
        emitDeprecationWarning();
        extension.getTestReportDir().set(project.getExtensions().getByType(ReportingExtension.class).getBaseDirectory().dir(testReportDirName));
    }

    @Override
    public SourceSetContainer getSourceSets() {
        emitDeprecationWarning();
        return extension.getSourceSets();
    }

    @Override
    public ProjectInternal getProject() {
        emitDeprecationWarning();
        return project;
    }

    @Override
    public void disableAutoTargetJvm() {
        emitDeprecationWarning();
        extension.disableAutoTargetJvm();
    }

    @Override
    public boolean getAutoTargetJvmDisabled() {
        emitDeprecationWarning();
        return extension.getAutoTargetJvmDisabled();
    }

    File getReportsDir() {
        emitDeprecationWarning();
        // This became public API by accident as Groovy has access to private methods and we show an example in our docs
        // see subprojects/docs/src/snippets/java/customDirs/groovy/build.gradle
        // and https://docs.gradle.org/current/userguide/java_testing.html#test_reporting
        return project.getExtensions().getByType(ReportingExtension.class).getBaseDir();
    }

    private static String relativePath(DirectoryProperty from, DirectoryProperty to) {
        return RelativePathUtil.relativePath(from.get().getAsFile(), to.get().getAsFile());
    }

    private static void emitDeprecationWarning() {
        DeprecationLogger.deprecate("Configuring Java defaults via the convention")
            .withAdvice("Java defaults should be configured via the extension instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(7, "all_convention_deprecation")
            .nagUser();
    }
}
