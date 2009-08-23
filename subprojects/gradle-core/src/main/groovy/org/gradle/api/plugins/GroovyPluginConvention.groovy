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

import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.UnionFileTree
import org.gradle.api.internal.file.DefaultSourceDirectorySet

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
    /**
     * All groovy/java source to be compiled for this project.
     */
    SourceDirectorySet groovySrc
    /**
     * All groovy/java test source to be compiled for this project.
     */
    SourceDirectorySet groovyTestSrc
    /**
     * All groovy source for this project.
     */
    UnionFileTree allGroovySrc
    /**
     * All groovy test source for this project.
     */
    UnionFileTree allGroovyTestSrc

    GroovyPluginConvention(Project project) {
        this.project = project
        groovySrcDirNames << 'main/groovy'
        groovyTestSrcDirNames << 'test/groovy'
        groovydocDirName = 'groovydoc'

        groovySrc = new DefaultSourceDirectorySet('main groovy source', project.fileResolver)
        groovySrc.srcDirs {-> groovySrcDirs}
        allGroovySrc = new UnionFileTree('main groovy source')
        allGroovySrc.add(groovySrc.matching {include '**/*.groovy'})
        javaConvention.source.main.allJava.add(groovySrc.matching {include '**/*.java'})

        groovyTestSrc = new DefaultSourceDirectorySet('test groovy source', project.fileResolver)
        groovyTestSrc.srcDirs {-> groovyTestSrcDirs}
        allGroovyTestSrc = new UnionFileTree('test groovy source')
        allGroovyTestSrc.add(groovyTestSrc.matching {include '**/*.groovy'})
        javaConvention.source.test.allJava.add(groovyTestSrc.matching {include '**/*.java'})
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
