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

/**
 * @author Hans Dockter
 */
class AntGroovyc {
    private static Logger logger = LoggerFactory.getLogger(AbstractAntCompile)

    // todo check this list after http://jira.codehaus.org/browse/GROOVY-2809 is resolved
    static final List NON_GROOVY_JAVAC_OPTIONS = ['includeJavaRuntime', 'optimize', 'failonerror', 'deprecation', 'fork', 'listfiles', 'nowarn', 'verbose', 'depend']

    public void execute(antNode, List sourceDirs, File targetDir, List classpath, String sourceCompatibility,
                        String targetCompatibility, CompileOptions compileOptions, List taskClasspath) {
        StringWriter stringWriter = new StringWriter()
        PrintWriter printWriter = new PrintWriter(stringWriter)
        classpath.each {
            logger.debug("Add $it to Ant classpath!")
            printWriter.println("pathelement(location: /$it/)")
        }
        String groovyc = """taskdef(name: 'groovyc', classname: 'org.codehaus.groovy.ant.Groovyc')
    mkdir(dir: /${targetDir.absolutePath}/)
    path(id: 'classpath_id') {
        $stringWriter
    }
    println System.getenv('JAVA_HOME')
    groovyc(
        includeAntRuntime: false,
        srcdir: /${sourceDirs.join(':')}/,
        destdir: /${targetDir}/,
        classpathref: 'classpath_id',
        verbose: true) {
        javac([source: '${sourceCompatibility}', target: '${targetCompatibility}'] + ${filterNonGroovycOptions(compileOptions)})
    }
"""
        logger.debug("Using groovyc as: $groovyc")
        GradleUtil.executeIsolatedAntScript(taskClasspath, groovyc)
    }

    private Map filterNonGroovycOptions(CompileOptions options) {
        // todo check if groupBy allows a more concise solution
        Map result = [:]
        options.optionMap().each {String key, String value ->
            if (!NON_GROOVY_JAVAC_OPTIONS.contains(key)) {
                result[key] = value
            }
        }
        result
    }

}
