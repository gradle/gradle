/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.api.plugins

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.internal.plugins.PluginUtil
import org.gradle.api.plugins.DefaultConventionsToPropertiesMapping
import org.gradle.api.tasks.bundling.*
import org.gradle.api.JavaVersion

/**
 * @author Hans Dockter
 */
// todo Think about moving the mkdir method to the project.
// todo Refactor to Java
class JavaPluginConvention {
    public static final Map DEFAULT_ARCHIVE_TYPES = [
            jar: new ArchiveType("jar", DefaultConventionsToPropertiesMapping.JAR, Jar),
            zip: new ArchiveType("zip", DefaultConventionsToPropertiesMapping.ZIP, Zip),
            war: new ArchiveType("war", DefaultConventionsToPropertiesMapping.WAR, War),
            tar: new ArchiveType("tar", DefaultConventionsToPropertiesMapping.TAR, Tar),
            'tar.gz': new ArchiveType("tar.gz", DefaultConventionsToPropertiesMapping.TAR, Tar),
            'tar.bzip2': new ArchiveType("tar.bzip2", DefaultConventionsToPropertiesMapping.TAR, Tar)
    ]

    Project project

    String srcRootName
    String srcDocsDirName
    String classesDirName
    String testClassesDirName
    String distsDirName
    String docsDirName
    String javadocDirName
    String testResultsDirName
    String reportsDirName
    String webAppDirName
    String testReportDirName
    List srcDirNames = []
    List resourceDirNames = []
    List testSrcDirNames = []
    List testResourceDirNames = []
    List floatingSrcDirs = []
    List floatingTestSrcDirs = []
    List floatingResourceDirs = []
    List floatingTestResourceDirs = []

    private JavaVersion srcCompat
    private JavaVersion targetCompat
    Map archiveTypes
    GradleManifest manifest
    List metaInf

    JavaPluginConvention(Project project, Map customValues) {
        this.project = project
        manifest = new GradleManifest()
        metaInf = []
        srcRootName = 'src'
        srcDocsDirName = 'docs'
        webAppDirName = 'main/webapp'
        classesDirName = 'classes'
        testClassesDirName = 'test-classes'
        distsDirName = 'distributions'
        docsDirName = 'docs'
        javadocDirName = 'javadoc'
        reportsDirName = 'reports'
        testResultsDirName = 'test-results'
        testReportDirName = 'tests'
        srcDirNames << 'main/java'
        resourceDirNames << 'main/resources'
        testSrcDirNames << 'test/java'
        testResourceDirNames << 'test/resources'
        archiveTypes = DEFAULT_ARCHIVE_TYPES
        PluginUtil.applyCustomValues(project.convention, this, customValues)
    }

    File mkdir(File parent = null, String name) {
        if (!name) {throw new InvalidUserDataException('You must specify the name of the directory')}
        File baseDir = parent ?: project.buildDir
        File result = new File(baseDir, name)
        result.mkdirs()
        result
    }

    File getSrcRoot() {
        project.file(srcRootName)
    }

    List getSrcDirs() {
        srcDirNames.collect {new File(srcRoot, it)} + floatingSrcDirs
    }

    List getResourceDirs() {
        resourceDirNames.collect {new File(srcRoot, it)} + floatingResourceDirs
    }

    List getTestSrcDirs() {
        testSrcDirNames.collect {new File(srcRoot, it)} + floatingTestSrcDirs
    }

    List getTestResourceDirs() {
        testResourceDirNames.collect {new File(srcRoot, it)} + floatingTestResourceDirs
    }

    File getSrcDocsDir() {
        new File(srcRoot, srcDocsDirName)
    }

    File getWebAppDir() {
        new File(srcRoot, webAppDirName)
    }

    File getClassesDir() {
        new File(project.buildDir, classesDirName)
    }

    File getTestClassesDir() {
        new File(project.buildDir, testClassesDirName)
    }

    File getDistsDir() {
        new File(project.buildDir, distsDirName)
    }

    File getDocsDir() {
        new File(project.buildDir, docsDirName)
    }

    File getJavadocDir() {
        new File(docsDir, javadocDirName)
    }

    File getTestResultsDir() {
        new File(project.buildDir, testResultsDirName)
    }

    File getTestReportDir() {
        new File(reportsDir, testReportDirName)
    }

    private File getReportsDir() {
        project.convention.plugins.reportingBase.reportsDir
    }

    JavaVersion getSourceCompatibility() {
        srcCompat ?: JavaVersion.VERSION_1_5
    }

    void setSourceCompatibility(def value) {
        srcCompat = JavaVersion.toVersion(value)
    }

    JavaVersion getTargetCompatibility() {
        targetCompat ?: sourceCompatibility
    }

    void setTargetCompatibility(def value) {
        targetCompat = JavaVersion.toVersion(value)
    }
}
