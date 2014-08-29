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

package org.gradle.api.tasks.javadoc

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.project.IsolatedAntBuilder

class AntGroovydoc {
    private final IsolatedAntBuilder ant
    private final ClassPathRegistry classPathRegistry

    def AntGroovydoc(IsolatedAntBuilder ant, ClassPathRegistry classPathRegistry) {
        this.ant = ant
        this.classPathRegistry = classPathRegistry
    }

    void execute(FileCollection source, File destDir, boolean use, String windowTitle,
                 String docTitle, String header, String footer, String overview, boolean includePrivate,
                 Set links, Iterable groovyClasspath, Iterable classpath, Project project) {

        def tmpDir = new File(project.buildDir, "tmp/groovydoc")
        project.delete tmpDir
        project.copy {
            from source
            into tmpDir
        }

        def args = [:]
        args.sourcepath = tmpDir.toString()
        args.destdir = destDir
        args.use = use
        args['private'] = includePrivate
        putIfNotNull(args, 'windowtitle', windowTitle)
        putIfNotNull(args, 'doctitle', docTitle)
        putIfNotNull(args, 'header', header)
        putIfNotNull(args, 'footer', footer)
        putIfNotNull(args, 'overview', overview)

        ant.withGroovy(groovyClasspath).withClasspath(classpath).execute {
            taskdef(name: 'groovydoc', classname: 'org.codehaus.groovy.ant.Groovydoc')
            groovydoc(args) {
                links.each {gradleLink ->
                    link(packages: gradleLink.packages.join(','), href: gradleLink.url)
                }
            }
        }
    }

    void putIfNotNull(Map map, String key, Object value) {
        if (value != null) {
            map.put(key, value)
        }
    }
}
