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

import org.gradle.api.Project
import org.gradle.api.internal.plugins.PluginUtil

/**
 * @author Hans Dockter
 */
class GroovyPluginConvention {
    Project project
    List groovySrcDirNames = []
    List groovyTestSrcDirNames = []
    List floatingGroovySrcDirs = []
    List floatingGroovyTestSrcDirs = []
    String groovydocDirName

    Closure groovyClasspath

    GroovyPluginConvention(Project project, Map customValues) {
        this.project = project
        groovySrcDirNames << 'main/groovy'
        groovyTestSrcDirNames << 'test/groovy'
        groovydocDirName = 'groovydoc'
        PluginUtil.applyCustomValues(project.convention, this, customValues)
    }

    List getGroovySrcDirs() {
        groovySrcDirNames.collect {new File(javaConvention.srcRoot, it)} + floatingGroovySrcDirs
    }

    List getGroovyTestSrcDirs() {
        groovyTestSrcDirNames.collect {new File(javaConvention.srcRoot, it)} + floatingGroovyTestSrcDirs
    }

    File getGroovydocDir() {
        return new File(javaConvention.docsDir, groovydocDirName)
    }
    
    private JavaPluginConvention getJavaConvention() {
        project.convention.plugins.java
    }
}
