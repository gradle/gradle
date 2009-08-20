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

package org.gradle.api.tasks.compile

import org.codehaus.groovy.ant.Groovyc
import org.gradle.api.tasks.util.AntTaskAccess
import org.gradle.util.BootstrapUtil
import org.gradle.util.GradleUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Please not: includeAntRuntime=false is ignored if groovyc is used in non fork mode. In this case the runtime classpath is
 * added to the compile classpath.
 * See: http://jira.codehaus.org/browse/GROOVY-2717
 *
 * @author Hans Dockter
 */
class AntGroovyc {
    private static Logger logger = LoggerFactory.getLogger(AntGroovyc)

    private final AntTaskAccess listener = new AntTaskAccess() { task->
        if (task instanceof Groovyc) {
            numFilesCompiled = task.fileList.length;
        }
    }
    int numFilesCompiled;

    List nonGroovycJavacOptions = ['verbose', 'deprecation', 'includeJavaRuntime', 'includeAntRuntime', 'optimize', 'fork', 'failonerror', 'listfiles', 'nowarn', 'depend']

    public void execute(antNode, List sourceDirs, List groovyIncludes, List groovyExcludes, List groovyJavaIncludes,
                        List groovyJavaExcludes, File targetDir, List classpath, String sourceCompatibility,
                        String targetCompatibility, GroovyCompileOptions groovyOptions, CompileOptions compileOptions, List taskClasspath) {

        String groovyc = """int numFilesCompiled = 0
    org.gradle.api.tasks.util.AntTaskAccess listener = new org.gradle.api.tasks.util.AntTaskAccess({ task->
        if (task != null && task instanceof org.codehaus.groovy.ant.Groovyc) {
            numFilesCompiled = task.fileList.length;
        }
    })
    ant.project.addBuildListener(listener)
    ant.taskdef(name: 'groovyc', classname: 'org.codehaus.groovy.ant.Groovyc')
    ant.mkdir(dir: '${GradleUtil.unbackslash(targetDir.absolutePath)}')
    ant.groovyc(
        [includeAntRuntime: false,
        srcdir: '${sourceDirs.collect {GradleUtil.unbackslash(it)}.join(':')}',
        destdir: '${GradleUtil.unbackslash(targetDir)}',
        classpath: '${(classpath + BootstrapUtil.antJarFiles).collect {GradleUtil.unbackslash(it)}.join(':')}'] +
        ${groovyOptions.quotedOptionMap()}) {
        ${groovyIncludes.collect {'include(name: \'' + it + '\')'}.join('\n')}
        ${groovyExcludes.collect {'exclude(name: \'' + it + '\')'}.join('\n')}
        javac([source: '${sourceCompatibility}', target: '${targetCompatibility}'] + ${filterNonGroovycOptions(compileOptions)}) {
            ${groovyJavaIncludes.collect {'include(name: \'' + it + '\')'}.join('\n')}
            ${groovyJavaExcludes.collect {'exclude(name: \'' + it + '\')'}.join('\n')}
        }
    }
    ant.project.removeBuildListener(listener)
    return numFilesCompiled
"""
        numFilesCompiled = (Integer)GradleUtil.executeIsolatedAntScript(taskClasspath, groovyc)
    }

    private Map filterNonGroovycOptions(CompileOptions options) {
        // todo check if groupBy allows a more concise solution
        Map result = [:]
        options.quotedOptionMap().each {String key, String value ->
            if (!nonGroovycJavacOptions.contains(key)) {
                result[key] = value
            }
        }
        result
    }
}
