/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.tasks.javadoc;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.ProjectInternal;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates groovy doc using Ant.
 */
public class AntGroovydoc {

    private final IsolatedAntBuilder ant;

    public AntGroovydoc(IsolatedAntBuilder ant, @SuppressWarnings("UnusedParameters") ClassPathRegistry ignored) {
        this.ant = ant;
    }

    public void execute(final FileCollection source, File destDir, boolean use, String windowTitle, String docTitle, String header, String footer, String overview, boolean includePrivate, final Set<Groovydoc.Link> links, final Iterable<File> groovyClasspath, Iterable<File> classpath, Project project) {

        final File tmpDir = new File(project.getBuildDir(), "tmp/groovydoc");
        FileOperations fileOperations = (ProjectInternal) project;
        fileOperations.delete(tmpDir);
        fileOperations.copy(new Action<CopySpec>() {
            public void execute(CopySpec copySpec) {
                copySpec.from(source).into(tmpDir);
            }
        });

        final Map<String, Object> args = Maps.newLinkedHashMap();
        args.put("sourcepath", tmpDir.toString());
        args.put("destdir", destDir);
        args.put("use", use);
        args.put("private", includePrivate);
        putIfNotNull(args, "windowtitle", windowTitle);
        putIfNotNull(args, "doctitle", docTitle);
        putIfNotNull(args, "header", header);
        putIfNotNull(args, "footer", footer);
        putIfNotNull(args, "overview", overview);

        List<File> combinedClasspath = ImmutableList.<File>builder()
                .addAll(classpath)
                .addAll(groovyClasspath)
                .build();

        ant.withClasspath(combinedClasspath).execute(new Closure<Object>(this, this) {
            @SuppressWarnings("UnusedDeclaration")
            public Object doCall(Object it) {
                final GroovyObjectSupport antBuilder = (GroovyObjectSupport) it;

                antBuilder.invokeMethod("taskdef", ImmutableMap.of(
                        "name", "groovydoc",
                        "classname", "org.codehaus.groovy.ant.Groovydoc"
                ));

                antBuilder.invokeMethod("groovydoc", new Object[]{args, new Closure<Object>(this, this) {
                    public Object doCall(Object ignore) {
                        for (Groovydoc.Link link : links) {
                            antBuilder.invokeMethod("link", new Object[]{
                                    ImmutableMap.of(
                                            "packages", Joiner.on(",").join(link.getPackages()),
                                            "href", link.getUrl()
                                    )
                            });
                        }

                        return null;
                    }
                }});

                return null;
            }
        });
    }

    private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

}
