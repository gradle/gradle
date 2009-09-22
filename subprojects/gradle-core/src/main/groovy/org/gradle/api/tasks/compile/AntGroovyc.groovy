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

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.project.IsolatedAntBuilder
import org.gradle.util.BootstrapUtil
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

    int numFilesCompiled;

    List nonGroovycJavacOptions = ['verbose', 'deprecation', 'includeJavaRuntime', 'includeAntRuntime', 'optimize', 'fork', 'failonerror', 'listfiles', 'nowarn', 'depend']

    public void execute(IsolatedAntBuilder antNode, FileCollection source, File targetDir, List classpath,
                        String sourceCompatibility, String targetCompatibility, GroovyCompileOptions groovyOptions,
                        CompileOptions compileOptions, List groovyClasspath) {
        // Force a particular Ant version. Also add in commons-cli, as the Groovy POM does not.
        List antBuilderClasspath = BootstrapUtil.antJarFiles + groovyClasspath + BootstrapUtil.commonsCliFiles
        antNode.execute(antBuilderClasspath) {
            taskdef(name: 'groovyc', classname: 'org.codehaus.groovy.ant.Groovyc')
            def task = groovyc([includeAntRuntime: false, destdir: targetDir, classpath: (classpath + antBuilderClasspath).join(File.pathSeparator)]
                    + groovyOptions.optionMap()) {
                source.addToAntBuilder(delegate, 'src', FileCollection.AntType.MatchingTask)
                javac([source: sourceCompatibility, target: targetCompatibility] + filterNonGroovycOptions(compileOptions))
            }
            numFilesCompiled = task.fileList.length
        }
    }

    private Map filterNonGroovycOptions(CompileOptions options) {
        // todo check if groupBy allows a more concise solution
        Map result = [:]
        options.optionMap().each {String key, Object value ->
            if (!nonGroovycJavacOptions.contains(key)) {
                result[key] = value
            }
        }
        result
    }
}
