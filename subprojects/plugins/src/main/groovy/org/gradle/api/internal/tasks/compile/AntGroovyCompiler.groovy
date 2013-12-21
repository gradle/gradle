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

package org.gradle.api.internal.tasks.compile

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.project.IsolatedAntBuilder
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.util.VersionNumber

/**
 * Please note: includeAntRuntime=false is ignored if groovyc is used in non fork mode. In this case the runtime classpath is
 * added to the compile classpath.
 * See: http://jira.codehaus.org/browse/GROOVY-2717
 */
class AntGroovyCompiler implements org.gradle.api.internal.tasks.compile.Compiler<GroovyJavaJointCompileSpec> {
    private final IsolatedAntBuilder ant
    private final ClassPathRegistry classPathRegistry

    List nonGroovycJavacOptions = ['verbose', 'deprecation', 'includeJavaRuntime', 'includeAntRuntime', 'optimize', 'fork', 'failonerror', 'listfiles', 'nowarn', 'depend']

    def AntGroovyCompiler(IsolatedAntBuilder ant, ClassPathRegistry classPathRegistry) {
        this.ant = ant;
        this.classPathRegistry = classPathRegistry;
    }

    WorkResult execute(GroovyJavaJointCompileSpec spec) {
        int numFilesCompiled;

        // Add in commons-cli, as the Groovy POM does not (for some versions of Groovy)
        Collection antBuilderClasspath = (spec.groovyClasspath as List) + classPathRegistry.getClassPath("COMMONS_CLI").asFiles

        def groovyVersion = sniffGroovyVersion(spec.groovyClasspath)
        // in Groovy 1.7.11, 1.8.7, and beyond, the combination of includeAntRuntime=false and fork=false is no longer allowed by the
        // groovyc Ant task and fails hard. That's why we have to enforce includeAntRuntime=true whenever fork=false in these versions,
        // even though this breaks some stuff. For example, compiling a class that extends GroovyTestCase runs into a NoClassDefFoundError:
        // org/junit/TestCase then.
        def includeAntRuntime = groovyVersion == VersionNumber.parse("1.7.11") || groovyVersion >= VersionNumber.parse("1.8.7") ? !spec.groovyCompileOptions.fork : false

        ant.withGroovy(antBuilderClasspath).execute {
            taskdef(name: 'groovyc', classname: 'org.codehaus.groovy.ant.Groovyc')
            def task = groovyc([includeAntRuntime: includeAntRuntime, destdir: spec.destinationDir, classpath: ((spec.classpath as List) + antBuilderClasspath).join(File.pathSeparator)]
                    + spec.groovyCompileOptions.optionMap()) {
                spec.source.addToAntBuilder(delegate, 'src', FileCollection.AntType.MatchingTask)
                javac([source: spec.sourceCompatibility, target: spec.targetCompatibility] + filterNonGroovycOptions(spec.compileOptions)) {
                    spec.compileOptions.compilerArgs.each {value ->
                        compilerarg(value: value)
                    }
                }
            }
            numFilesCompiled = task.fileList.length
        }

        return { numFilesCompiled > 0 } as WorkResult
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

    private VersionNumber sniffGroovyVersion(Iterable<File> classpath) {
        def classLoader = new URLClassLoader(classpath*.toURI()*.toURL() as URL[], (ClassLoader) null)
        try {
            def clazz = classLoader.loadClass("groovy.lang.GroovySystem")
            return VersionNumber.parse(clazz.getVersion())
        } catch (ClassNotFoundException ignored) {
            return VersionNumber.UNKNOWN
        } catch (LinkageError ignored) {
            return VersionNumber.UNKNOWN
        }
    }
}
