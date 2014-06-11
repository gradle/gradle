/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.tasks.scala

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.project.IsolatedAntBuilder
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.api.tasks.WorkResult
import org.gradle.util.GUtil
import org.gradle.util.VersionNumber
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class AntScalaCompiler implements Compiler<ScalaCompileSpec> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AntScalaCompiler)

    private final IsolatedAntBuilder antBuilder
    private final Iterable<File> bootclasspathFiles
    private final Iterable<File> extensionDirs

    def AntScalaCompiler(IsolatedAntBuilder antBuilder) {
        this.antBuilder = antBuilder
        this.bootclasspathFiles = []
        this.extensionDirs = []
    }

    def AntScalaCompiler(IsolatedAntBuilder antBuilder, Iterable<File> bootclasspathFiles, Iterable<File> extensionDirs) {
        this.antBuilder = antBuilder
        this.bootclasspathFiles = bootclasspathFiles
        this.extensionDirs = extensionDirs
    }

    WorkResult execute(ScalaCompileSpec spec) {
        def destinationDir = spec.destinationDir
        def scalaCompileOptions = spec.scalaCompileOptions

        def backend = chooseBackend(spec)
        def options = [destDir: destinationDir, target: backend] + scalaCompileOptions.optionMap()
        if (scalaCompileOptions.fork) {
            options.compilerPath = GUtil.asPath(spec.scalaClasspath)
        }
        def taskName = scalaCompileOptions.useCompileDaemon ? 'fsc' : 'scalac'
        def compileClasspath = spec.classpath

        LOGGER.info("Compiling with Ant scalac task.")
        LOGGER.debug("Ant scalac task options: {}", options)

        antBuilder.withClasspath(spec.scalaClasspath).execute { ant ->
            taskdef(resource: 'scala/tools/ant/antlib.xml')

            "${taskName}"(options) {
                spec.source.addToAntBuilder(ant, 'src', FileCollection.AntType.MatchingTask)
                bootclasspathFiles.each {file ->
                    bootclasspath(location: file)
                }
                extensionDirs.each {dir ->
                    extdirs(location: dir)
                }
                compileClasspath.each {file ->
                    classpath(location: file)
                }
            }
        }

        return { true } as WorkResult
    }

    private VersionNumber sniffScalaVersion(Iterable<File> classpath) {
        def classLoader = new URLClassLoader(classpath*.toURI()*.toURL() as URL[], (ClassLoader) null)
        try {
            def clazz = classLoader.loadClass("scala.util.Properties")
            return VersionNumber.parse(clazz.scalaPropOrEmpty("maven.version.number"))
        } catch (ClassNotFoundException ignored) {
            return VersionNumber.UNKNOWN
        } catch (LinkageError ignored) {
            return VersionNumber.UNKNOWN
        }
    }

    private String chooseBackend(ScalaCompileSpec spec) {
        def maxSupported
        def scalaVersion = sniffScalaVersion(spec.scalaClasspath)
        if (scalaVersion >= VersionNumber.parse("2.10.0-M5")) {
            maxSupported = VersionNumber.parse("1.7")
        } else {
            // prior to Scala 2.10.0-M5, scalac Ant task only supported "jvm-1.5" and "msil" backends
            maxSupported = VersionNumber.parse("1.5")
        }

        def target = VersionNumber.parse(spec.targetCompatibility)
        if (target > maxSupported) {
            target = maxSupported
        }

        return "jvm-${target.major}.${target.minor}"
    }
}
