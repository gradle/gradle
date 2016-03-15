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

package org.gradle.api.tasks.gosu;

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GosuDoc helper for Ant
 */
public class AntGosuDoc {
    private static final Logger LOGGER = LoggerFactory.getLogger(AntGosuDoc.class);

    private final IsolatedAntBuilder _antBuilder;

    public AntGosuDoc(IsolatedAntBuilder antBuilder) {
        _antBuilder = antBuilder;
    }

    public WorkResult execute(final FileCollection source, final File targetDir, Iterable<File> classpathFiles, Iterable<File> gosuClasspath, GosuDocOptions options, Project project) {
        final String gosuClasspathRefId = "gosu.classpath";
        final Map<String, Object> optionsMap = options.optionMap();

        final String taskName = "gosudoc";

        LOGGER.info("Creating GosuDoc Ant gosudoc task.");
        LOGGER.info("Ant gosudoc task options: {}", optionsMap);

        final List<File> jointClasspath = new ArrayList<File>();
        jointClasspath.add(getToolsJar());
        for(File file : classpathFiles) {
            jointClasspath.add(file);
        }
        for(File file : gosuClasspath) {
            jointClasspath.add(file);
        }
        LOGGER.info("Ant gosudoc jointClasspath: {}", jointClasspath);

        //'source' is a FileCollection with explicit paths.
        // We don't want that, so instead we create a temp directory with the contents of 'source'
        // Copying 'source' to the temp dir should honor its include/exclude patterns
        // Finally, the tmpdir will be the sole inputdir passed to the gosudoc task

        final File tmpDir = new File(project.getBuildDir(), "tmp/gosudoc");
        FileOperations fileOperations = (ProjectInternal) project;
        fileOperations.delete(tmpDir);
        fileOperations.copy(new Action<CopySpec>() {
            public void execute(CopySpec copySpec) {
                copySpec.from(source).into(tmpDir);
            }
        });

        _antBuilder.withClasspath(jointClasspath).execute(new Closure<Object>(this, this) {
            @SuppressWarnings("UnusedDeclaration")
            public Object doCall(Object it) {
                final GroovyObjectSupport antBuilder = (GroovyObjectSupport) it;

                LOGGER.info("About to call antBuilder.invokeMethod(\"taskdef\")");

                Map<String, Object> taskdefMap = new HashMap<String, Object>();
                taskdefMap.put("name", taskName);
                taskdefMap.put("classname", "gosu.tools.ant.Gosudoc"); //TODO load from antlib.xml

                antBuilder.invokeMethod("taskdef", taskdefMap);

                LOGGER.info("Finished calling antBuilder.invokeMethod(\"taskdef\")");

                //define the PATH for the classpath
                List<String> gosuClasspathAsStrings = new ArrayList<String>();
                for(File file : jointClasspath) {
                    gosuClasspathAsStrings.add(file.getAbsolutePath());
                }

                Map<String, Object> classpath = new HashMap<String, Object>();
                classpath.put("id", gosuClasspathRefId);
                classpath.put("path", String.join(":", gosuClasspathAsStrings));

                LOGGER.info("About to call antBuilder.invokeMethod(\"path\")");
                LOGGER.info("classpath map {}", classpath);

                antBuilder.invokeMethod("path", classpath);

                LOGGER.info("Finished calling antBuilder.invokeMethod(\"path\")");

                optionsMap.put("inputdirs", tmpDir.getAbsolutePath()); //TODO or use 'String.join(":", srcDirAsStrings)' ??
                optionsMap.put("outputdir", targetDir);
                optionsMap.put("classpathref", gosuClasspathRefId);

                LOGGER.info("Dumping optionsMap:");
                for(String k : optionsMap.keySet()) {
                    LOGGER.info('\t' + k + '=' + optionsMap.get(k));
                }

                LOGGER.debug("About to call antBuilder.invokeMethod(\"" + taskName + "\")");

                antBuilder.invokeMethod(taskName, optionsMap);

                return null;
            }
        });

        return new SimpleWorkResult(true);
    }

    /**
     * Get all tools.jar from the lib directory of the System's java.home property
     * @return File reference to tools.jar
     */
    private File getToolsJar() {
        String javaHome = System.getProperty("java.home");
        java.nio.file.Path libsDir = FileSystems.getDefault().getPath(javaHome, "/lib");
        return new File(libsDir.toFile(), "tools.jar");
    }
}
