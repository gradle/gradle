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
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultSourceSetContainer;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.internal.Actions;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.testing.base.plugins.TestingBasePlugin;

import java.io.File;

import static org.gradle.api.reflect.TypeOf.typeOf;
import static org.gradle.util.ConfigureUtil.configure;

public class DefaultJavaPluginConvention extends JavaPluginConvention implements HasPublicType {
    private ProjectInternal project;

    private String docsDirName;

    private String testResultsDirName;

    private String testReportDirName;

    private final SourceSetContainer sourceSets;

    private JavaVersion srcCompat;
    private JavaVersion targetCompat;

    public DefaultJavaPluginConvention(ProjectInternal project, Instantiator instantiator, CollectionCallbackActionDecorator collectionCallbackActionDecorator) {
        this.project = project;
        sourceSets = instantiator.newInstance(DefaultSourceSetContainer.class, project.getFileResolver(), project.getTasks(), instantiator, project.getServices().get(ObjectFactory.class), collectionCallbackActionDecorator);
        docsDirName = "docs";
        testResultsDirName = TestingBasePlugin.TEST_RESULTS_DIR_NAME;
        testReportDirName = TestingBasePlugin.TESTS_DIR_NAME;
    }

    @Override
    public TypeOf<?> getPublicType() {
        return typeOf(JavaPluginConvention.class);
    }

    @Override
    public Object sourceSets(Closure closure) {
        return sourceSets.configure(closure);
    }

    @Override
    public File getDocsDir() {
        return project.getServices().get(FileLookup.class).getFileResolver(project.getBuildDir()).resolve(docsDirName);
    }

    @Override
    public File getTestResultsDir() {
        return project.getServices().get(FileLookup.class).getFileResolver(project.getBuildDir()).resolve(testResultsDirName);
    }

    @Override
    public File getTestReportDir() {
        return project.getServices().get(FileLookup.class).getFileResolver(getReportsDir()).resolve(testReportDirName);
    }

    private File getReportsDir() {
        return project.getExtensions().getByType(ReportingExtension.class).getBaseDir();
    }

    @Override
    public JavaVersion getSourceCompatibility() {
        return srcCompat != null ? srcCompat : JavaVersion.current();
    }

    @Override
    public void setSourceCompatibility(Object value) {
        setSourceCompatibility(JavaVersion.toVersion(value));
    }

    @Override
    public void setSourceCompatibility(JavaVersion value) {
        srcCompat = value;
    }

    @Override
    public JavaVersion getTargetCompatibility() {
        return targetCompat != null ? targetCompat : getSourceCompatibility();
    }

    @Override
    public void setTargetCompatibility(Object value) {
        setTargetCompatibility(JavaVersion.toVersion(value));
    }

    @Override
    public void setTargetCompatibility(JavaVersion value) {
        targetCompat = value;
    }

    @Override
    public Manifest manifest() {
        return manifest(Actions.<Manifest>doNothing());
    }

    @Override
    public Manifest manifest(Closure closure) {
        return configure(closure, createManifest());
    }

    @Override
    public Manifest manifest(Action<? super Manifest> action) {
        Manifest manifest = createManifest();
        action.execute(manifest);
        return manifest;
    }

    private Manifest createManifest() {
        return new DefaultManifest(project.getFileResolver());
    }

    @Override
    public String getDocsDirName() {
        return docsDirName;
    }

    @Override
    public void setDocsDirName(String docsDirName) {
        this.docsDirName = docsDirName;
    }

    @Override
    public String getTestResultsDirName() {
        return testResultsDirName;
    }

    @Override
    public void setTestResultsDirName(String testResultsDirName) {
        this.testResultsDirName = testResultsDirName;
    }

    @Override
    public String getTestReportDirName() {
        return testReportDirName;
    }

    @Override
    public void setTestReportDirName(String testReportDirName) {
        this.testReportDirName = testReportDirName;
    }

    @Override
    public SourceSetContainer getSourceSets() {
        return sourceSets;
    }

    @Override
    public ProjectInternal getProject() {
        return project;
    }
}
