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

package org.gradle.build.release

import org.gradle.api.Project

/**
 * @author Hans Dockter
 */
class Version {
    File propertyFile

    Project project

    boolean majorNotMinor

    Svn svn

    int majorInternal, minorInternal, revisionInternal

    Version(Svn svn, Project project, boolean majorNotMinor) {
        this.project = project
        this.majorNotMinor = majorNotMinor
        this.svn = svn
        majorInternal = project.hasProperty('previousMajor') ? project.previousMajor.toInteger() : 0
        minorInternal = project.hasProperty('previousMinor') ? project.previousMinor.toInteger() : 0
        revisionInternal = project.hasProperty('previousRevision') ? project.previousRevision.toInteger() : 0
    }

    void storeCurrentVersion() {
        Properties properties = new Properties()
        propertyFile = new File(project.projectDir, 'gradle.properties')
        if (propertyFile.isFile()) {
            FileInputStream propertiesInputStream = new FileInputStream(propertyFile)
            properties.load(propertiesInputStream)
            propertiesInputStream.close()
        }
        properties['previousMajor'] = major as String
        properties['previousMinor'] = minor as String
        properties['previousRevision'] = revision as String
        FileOutputStream propertiesOutputStream = new FileOutputStream(propertyFile)
        properties.store(propertiesOutputStream, null)
        propertiesOutputStream.close()
    }

    int getMajor() {
        majorNotMinor && svn.isTrunk() ? majorInternal + 1 : majorInternal
    }

    int getMinor() {
        if (svn.isTrunk()) {
            return majorNotMinor ? 0 : minorInternal + 1
        } else {
            return minorInternal
        }
    }

    int getRevision() {
        svn.isTrunk() ? 0 : revisionInternal + 1
    }

    String getLastRelease() {
        versionString(majorInternal, minorInternal, revisionInternal)
    }

    String toString() {
        "${versionString(major, minor, revision)}${project.versionModifier ? '-' + project.versionModifier : ''}"
    }

    private String versionString(int major, int minor, int revision) {
        "${major}.${minor}${revision ? '.' + revision : ''}"
    }
}
