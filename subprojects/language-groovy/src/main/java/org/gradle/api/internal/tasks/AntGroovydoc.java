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

package org.gradle.api.internal.tasks;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.tasks.javadoc.Groovydoc;
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates groovy doc using Ant.
 */
public final class AntGroovydoc {

    private final IsolatedAntBuilder ant;
    private final TemporaryFileProvider temporaryFileProvider;

    public AntGroovydoc(
        IsolatedAntBuilder ant,
        TemporaryFileProvider temporaryFileProvider
    ) {
        this.ant = ant;
        this.temporaryFileProvider = temporaryFileProvider;
    }

    public void execute(
        final FileCollection source, File destDir, boolean use, boolean noTimestamp, boolean noVersionStamp,
        String windowTitle, String docTitle, String header, String footer, String overview, boolean includePrivate,
        final Set<Groovydoc.Link> links, final Iterable<File> groovyClasspath, Iterable<File> classpath,
        File tmpDir, FileSystemOperations fsOperations
    ) {

        fsOperations.delete(spec -> spec.delete(tmpDir));
        fsOperations.copy(spec -> spec.from(source).into(tmpDir));

        List<File> combinedClasspath = ImmutableList.<File>builder()
            .addAll(classpath)
            .addAll(groovyClasspath)
            .build();

        VersionNumber version = VersionNumber.parse(getGroovyVersion(combinedClasspath));

        final Map<String, Object> args = Maps.newLinkedHashMap();
        args.put("sourcepath", tmpDir.toString());
        args.put("destdir", destDir);
        args.put("use", use);
        if (isAtLeast(version, "2.4.6")) {
            args.put("noTimestamp", noTimestamp);
            args.put("noVersionStamp", noVersionStamp);
        }
        args.put("private", includePrivate);
        putIfNotNull(args, "windowtitle", windowTitle);
        putIfNotNull(args, "doctitle", docTitle);
        putIfNotNull(args, "header", header);
        putIfNotNull(args, "footer", footer);

        if (overview != null) {
            args.put("overview", overview);
        }

        invokeGroovydoc(links, combinedClasspath, args);
    }

    private boolean isAtLeast(VersionNumber version, String versionString) {
        return version.compareTo(VersionNumber.parse(versionString)) >= 0;
    }

    private String getGroovyVersion(List<File> combinedClasspath) {
        File temp;
        final String tempPath;
        try {
            temp = temporaryFileProvider.createTemporaryFile("temp", "");
            String p = temp.getCanonicalPath();
            tempPath = File.separatorChar == '/' ? p : p.replace(File.separatorChar, '/');
            temp.deleteOnExit();
        } catch (IOException e) {
            throw new GradleException("Unable to create temp file needed for Groovydoc", e);
        }

        ant.withClasspath(combinedClasspath).execute(new Closure<Object>(this, this) {
            @SuppressWarnings("UnusedDeclaration")
            public Object doCall(Object it) {
                final GroovyObjectSupport antBuilder = (GroovyObjectSupport) it;

                antBuilder.invokeMethod("taskdef", ImmutableMap.of(
                    "name", "groovy",
                    "classname", "org.codehaus.groovy.ant.Groovy"
                ));

                antBuilder.invokeMethod("groovy", new Object[]{"new File('" + tempPath + "').text = GroovySystem.version"});

                return null;
            }
        });
        try {
            return Files.asCharSource(temp, Charset.defaultCharset()).read().trim();
        } catch (IOException e) {
            throw new GradleException("Unable to find Groovy version needed for Groovydoc", e);
        }
    }

    private void invokeGroovydoc(final Set<Groovydoc.Link> links, List<File> combinedClasspath, final Map<String, Object> args) {
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
