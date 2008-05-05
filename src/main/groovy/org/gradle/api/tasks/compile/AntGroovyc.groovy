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

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.gradle.util.GradleUtil
import org.apache.commons.io.FilenameUtils

/**
 * @author Hans Dockter
 */
class AntGroovyc {
    private static Logger logger = LoggerFactory.getLogger(AbstractAntCompile)

    // todo check this list after http://jira.codehaus.org/browse/GROOVY-2809 is resolved
    List nonGroovycJavacOptions = ['includeJavaRuntime', 'optimize', 'failonerror', 'deprecation', 'fork', 'listfiles', 'nowarn', 'verbose', 'depend']
    

    public void execute(antNode, List sourceDirs, File targetDir, List classpath, String sourceCompatibility,
                        String targetCompatibility, CompileOptions compileOptions, List taskClasspath) {
        String groovyc = """taskdef(name: 'groovyc', classname: 'org.codehaus.groovy.ant.Groovyc')
    mkdir(dir: '${unbackslash(targetDir.absolutePath)}')
    groovyc(
        includeAntRuntime: false,
        srcdir: '${sourceDirs.collect {unbackslash(it)}.join(':')}',
        destdir: '${unbackslash(targetDir)}',
        classpath: '${classpath.collect {unbackslash(it)}.join(':')}',
        verbose: true) {
        javac([source: '${sourceCompatibility}', target: '${targetCompatibility}'] + ${filterNonGroovycOptions(compileOptions)})
    }
"""
        GradleUtil.executeIsolatedAntScript(taskClasspath, groovyc)
    }

    private Map filterNonGroovycOptions(CompileOptions options) {
        // todo check if groupBy allows a more concise solution
        Map result = [:]
        options.optionMap().each {String key, String value ->
            if (!nonGroovycJavacOptions.contains(key)) {
                result[key] = value
            }
        }
        result
    }

    private String unbackslash(def s) {
        FilenameUtils.separatorsToUnix(s.toString())
    }

}
