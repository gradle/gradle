/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.gosu;

import com.google.common.base.Joiner;
import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.gosu.GosuCompileOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AntGosuCompiler implements Compiler<GosuCompileSpec> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AntGosuCompiler.class);

    private final IsolatedAntBuilder antBuilder;
    private Iterable<File> compileClasspath;
    private Iterable<File> gosuClasspath;
    private final String projectName;

    public AntGosuCompiler(IsolatedAntBuilder antBuilder, Iterable<File> compileClasspath, Iterable<File> gosuClasspath) {
        this(antBuilder, compileClasspath, gosuClasspath, "");
    }

    public AntGosuCompiler(IsolatedAntBuilder antBuilder, Iterable<File> compileClasspath, Iterable<File> gosuClasspath, String projectName) {
        this.antBuilder = antBuilder;
        this.compileClasspath = compileClasspath;
        this.gosuClasspath = gosuClasspath;
        this.projectName = projectName;
    }

    @Override
    public WorkResult execute(final GosuCompileSpec spec) {
        final String gosuClasspathRefId = "gosu.classpath";
        final File destinationDir = spec.getDestinationDir();
        final GosuCompileOptions options = (GosuCompileOptions) spec.getGosuCompileOptions();
        final Map<String, Object> optionsMap = options.optionMap();

        final String taskName = "gosuc";

        LOGGER.info("Compiling with Ant gosuc task.");
        LOGGER.info("Ant gosuc task options: {}", optionsMap);
        LOGGER.info("compileClasspath: {}", compileClasspath);
        LOGGER.info("gosuClasspath: {}", gosuClasspath);

        final List<File> jointClasspath = new ArrayList<File>();
        for(File file : compileClasspath) {
            jointClasspath.add(file);
        }
        for(File file : gosuClasspath) {
            jointClasspath.add(file);
        }

        LOGGER.info("jointClasspath: {}", jointClasspath);

        antBuilder.withClasspath(jointClasspath).execute(new Closure<Object>(this, this) {
            @SuppressWarnings("UnusedDeclaration")
            public Object doCall(Object it) {
                final GroovyObjectSupport antBuilder = (GroovyObjectSupport) it;

                LOGGER.debug("About to call antBuilder.invokeMethod(\"taskdef\")");

//        antBuilder.invokeMethod("taskdef", ImmutableMap.of(
//            "resource", "gosu/tools/ant/antlib.xml"
//        ));
                Map<String, Object> taskdefMap = new HashMap<String, Object>();
                taskdefMap.put("name", taskName);
                taskdefMap.put("classname", "gosu.tools.ant.Gosuc");  //TODO load from antlib.xml

                antBuilder.invokeMethod("taskdef", taskdefMap);

                LOGGER.debug("Finished calling antBuilder.invokeMethod(\"taskdef\")");

                //define the PATH for the classpath
                Map<String, Object> classpath = new HashMap<String, Object>();
                classpath.put("id", gosuClasspathRefId);
                classpath.put("path", Joiner.on(':').join(jointClasspath));

                LOGGER.debug("About to call antBuilder.invokeMethod(\"path\")");
                LOGGER.debug("classpath map {}", classpath);

                antBuilder.invokeMethod("path", classpath);

                LOGGER.debug("Finished calling antBuilder.invokeMethod(\"path\")");

                optionsMap.put("destdir", destinationDir.getAbsolutePath());
                optionsMap.put("classpathref", gosuClasspathRefId);
                optionsMap.put("projectname", projectName);

                LOGGER.debug("Dumping optionsMap:");
                for(String k : optionsMap.keySet()) {
                    LOGGER.debug('\t' + k + '=' + optionsMap.get(k));
                }

                LOGGER.debug("About to call antBuilder.invokeMethod(\"" + taskName + "\")");

                antBuilder.invokeMethod(taskName, new Object[]{optionsMap, new Closure<Object>(this, this) {
                    public Object doCall(Object ignore) {
                        spec.getSource().addToAntBuilder(antBuilder, "src", FileCollection.AntType.MatchingTask);

                        return null;
                    }
                }});

                return null;
            }
        });

        return new SimpleWorkResult(true);
    }
}
