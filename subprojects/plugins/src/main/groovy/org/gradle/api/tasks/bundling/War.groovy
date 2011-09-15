/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.tasks.bundling

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.util.ConfigureUtil
import org.gradle.api.internal.file.copy.CopySpecImpl

/**
 * Assembles a WAR archive.
 *
 * @author Hans Dockter
 */
class War extends Jar {
    public static final String WAR_EXTENSION = 'war'

    private File webXml

    private FileCollection classpath
    private final CopySpecImpl webInf

    War() {
        extension = WAR_EXTENSION
        // Add these as separate specs, so they are not affected by the changes to the main spec
        webInf = copyAction.rootSpec.addChild().into('WEB-INF')
        webInf.into('classes') {
            from {
                def classpath = getClasspath()
                classpath ? classpath.filter {File file -> file.isDirectory()} : []
            }
        }
        webInf.into('lib') {
            from {
                def classpath = getClasspath()
                classpath ? classpath.filter {File file -> file.isFile()} : []
            }
        }
        webInf.into('') {
            from {
                getWebXml()
            }
            rename {
                'web.xml'
            }
        }
    }

    CopySpec getWebInf() {
        return webInf.addChild()
    }

    /**
     * Adds some content to the {@code WEB-INF} directory for this WAR archive.
     *
     * <p>The given closure is executed to configure a {@link CopySpec}. The {@code CopySpec} is passed to the closure
     * as its delegate.
     *
     * @param configureClosure The closure to execute
     * @return The newly created {@code CopySpec}.
     */
    CopySpec webInf(Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, getWebInf())
    }

    /**
     * Returns the classpath to include in the WAR archive. Any JAR or ZIP files in this classpath are included in the
     * {@code WEB-INF/lib} directory. Any directories in this classpath are included in the {@code WEB-INF/classes}
     * directory.
     *
     * @return The classpath. Returns an empty collection when there is no classpath to include in the WAR.
     */
    @InputFiles @Optional
    FileCollection getClasspath() {
        return classpath
    }

    /**
     * Sets the classpath to include in the WAR archive.
     *
     * @param classpath The classpath. Must not be null.
     */
    void setClasspath(Object classpath) {
        this.classpath = project.files(classpath)
    }

    /**
     * Adds files to the classpath to include in the WAR archive.
     *
     * @param classpath The files to add. These are evaluated as for {@link org.gradle.api.Project#files(Object [])}
     */
    void classpath(Object... classpath) {
        FileCollection oldClasspath = getClasspath()
        this.classpath = project.files(oldClasspath ?: [], classpath)
    }

    /**
     * Returns the {@code web.xml} file to include in the WAR archive. When {@code null}, no {@code web.xml} file is
     * included in the WAR.
     *
     * @return The {@code web.xml} file.
     */
    @InputFile @Optional
    public File getWebXml() {
        return webXml;
    }

    /**
     * Sets the {@code web.xml} file to include in the WAR archive. When {@code null}, no {@code web.xml} file is
     * included in the WAR.
     *
     * @param webXml The {@code web.xml} file. Maybe null.
     */
    public void setWebXml(File webXml) {
        this.webXml = webXml;
    }

    /**
     * Re-enables the jar task, includes the jar as part of the war instead of
     * of the classes, and replaces the war with the jar in the archives
     * (which is the normal archive object for the java plugin).
     */
    public void restoreJar() {
        Configuration archivesConfiguration = project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION);

        // REVISIT: jar task name is located in plugin class, which means it
        //          cannot be directly used here
        Jar jarTask = (Jar) project.getTasks().getByName("jar");
        reenableJarTaskAndRestoreToArchivesConfiguration(archivesConfiguration, jarTask);

        // REVISIT: war task name is located in plugin class, which means it
        //          cannot be directly used here
        War warTask = (War) project.getTasks().getByName("war");
        removeWarTaskFromArchivesConfiguration(archivesConfiguration, warTask);

        classpath = jarTask.outputs.files + project.configurations.runtime - project.configurations.providedRuntime;
    }

  private void reenableJarTaskAndRestoreToArchivesConfiguration(Configuration archivesConfiguration, Jar jarTask) {
        jarTask.setEnabled(true);
        restoreJarTaskToArchivesConfiguration(archivesConfiguration, jarTask);
    }

    private void restoreJarTaskToArchivesConfiguration(Configuration archivesConfiguration, Jar jar) {
        boolean jarAlreadyRestored = false;
        for (PublishArtifact publishArtifact : archivesConfiguration.getAllArtifacts()) {
            if (publishArtifact instanceof ArchivePublishArtifact) {
                ArchivePublishArtifact archivePublishArtifact = (ArchivePublishArtifact) publishArtifact;
                if (archivePublishArtifact.getArchiveTask() == jar) {
                    // If found, then do not re-add
                    jarAlreadyRestore = true;
                    break;
                }
            }
        }

        if (! jarAlreadyRestored){
            archivesConfiguration.getArtifacts().add(new ArchivePublishArtifact(jar));
        }
    }

    private void removeWarTaskFromArchivesConfiguration(Configuration archivesConfiguration, War war) {
        // todo: There should be a richer connection between an ArchiveTask and a PublishArtifact
        for (PublishArtifact publishArtifact : archivesConfiguration.getAllArtifacts()) {
            if (publishArtifact instanceof ArchivePublishArtifact) {
                ArchivePublishArtifact archivePublishArtifact = (ArchivePublishArtifact) publishArtifact;
                if (archivePublishArtifact.getArchiveTask() == war) {
                    archivesConfiguration.getArtifacts().remove(publishArtifact);
                }
            }
        }
    }
}
