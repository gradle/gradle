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

import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.util.ConfigureUtil
import org.gradle.api.internal.file.copy.CopySpecImpl

/**
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

    CopySpec webInf(Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, getWebInf())
    }

    @InputFiles @Optional
    FileCollection getClasspath() {
        return classpath
    }

    void setClasspath(Object classpath) {
        this.classpath = project.files(classpath)
    }

    void classpath(Object... classpath) {
        FileCollection oldClasspath = getClasspath()
        this.classpath = project.files(oldClasspath ?: [], classpath)
    }

    @InputFile @Optional
    public File getWebXml() {
        return webXml;
    }

    public void setWebXml(File webXml) {
        this.webXml = webXml;
    }
}
