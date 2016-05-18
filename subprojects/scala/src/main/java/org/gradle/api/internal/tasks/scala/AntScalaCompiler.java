/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks.scala;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.GUtil;
import org.gradle.util.VersionNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AntScalaCompiler extends GroovyObjectSupport implements Compiler<ScalaCompileSpec> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AntScalaCompiler.class);

    private final IsolatedAntBuilder antBuilder;
    private final List<File> scalaClasspath;

    public AntScalaCompiler(IsolatedAntBuilder antBuilder, Iterable<File> scalaClasspath) {
        this.scalaClasspath = ImmutableList.copyOf(scalaClasspath);
        this.antBuilder = antBuilder;
    }

    public WorkResult execute(final ScalaCompileSpec spec) {
        File destinationDir = spec.getDestinationDir();
        ScalaCompileOptionsInternal scalaCompileOptions = (ScalaCompileOptionsInternal) spec.getScalaCompileOptions();

        String backend = chooseBackend(spec);
        ImmutableMap.Builder<String, Object> optionsBuilder = ImmutableMap.builder();
        optionsBuilder.put("destDir", destinationDir);
        optionsBuilder.put("target", backend);
        optionsBuilder.putAll(scalaCompileOptions.optionMap());
        if (scalaCompileOptions.internalIsFork()) {
            optionsBuilder.put("compilerPath", GUtil.asPath(scalaClasspath));
        }
        final ImmutableMap<String, Object> options = optionsBuilder.build();
        final String taskName = scalaCompileOptions.internalUseCompileDaemon() ? "fsc" : "scalac";
        final Iterable<File> compileClasspath = spec.getClasspath();

        LOGGER.info("Compiling with Ant scalac task.");
        LOGGER.debug("Ant scalac task options: {}", options);

        antBuilder.withClasspath(scalaClasspath).execute(new Closure<Object>(this) {
            @SuppressWarnings("unused")
            public Object doCall(final AntBuilderDelegate ant) {
                ant.invokeMethod("taskdef", Collections.singletonMap("resource", "scala/tools/ant/antlib.xml"));

                return ant.invokeMethod(taskName, new Object[]{options, new Closure<Void>(this) {
                    public void doCall() {
                        spec.getSource().addToAntBuilder(ant, "src", FileCollection.AntType.MatchingTask);
                        for (File file : compileClasspath) {
                            ant.invokeMethod("classpath", Collections.singletonMap("location", file));
                        }
                    }
                }});
            }
        });

        return new SimpleWorkResult(true);
    }

    private static VersionNumber sniffScalaVersion(Collection<File> classpath) {
        URL[] urls = Collections2.transform(classpath, new Function<File, URL>() {
            @Override
            public URL apply(File file) {
                try {
                    return file.toURI().toURL();
                } catch (MalformedURLException e) {
                    throw Throwables.propagate(e);
                }
            }
        }).toArray(new URL[0]);
        URLClassLoader classLoader = new URLClassLoader(urls, null);
        try {
            Class<?> clazz = classLoader.loadClass("scala.util.Properties");
            String versionNumber = (String) InvokerHelper.invokeMethod(clazz, "scalaPropOrEmpty", "maven.version.number");
            return VersionNumber.parse(versionNumber);
        } catch (ClassNotFoundException ignored) {
            return VersionNumber.UNKNOWN;
        } catch (LinkageError ignored) {
            return VersionNumber.UNKNOWN;
        }
    }

    private String chooseBackend(ScalaCompileSpec spec) {
        VersionNumber maxSupported;
        VersionNumber scalaVersion = sniffScalaVersion(scalaClasspath);
        if (scalaVersion.compareTo(VersionNumber.parse("2.10.0-M5")) >= 0) {
            maxSupported = VersionNumber.parse("1.7");
        } else {
            // prior to Scala 2.10.0-M5, scalac Ant task only supported "jvm-1.5" and "msil" backends
            maxSupported = VersionNumber.parse("1.5");
        }

        VersionNumber target = VersionNumber.parse(spec.getTargetCompatibility());
        if (target.compareTo(maxSupported) > 0) {
            target = maxSupported;
        }

        return "jvm-" + target.getMajor() + "." + target.getMinor();
    }
}
