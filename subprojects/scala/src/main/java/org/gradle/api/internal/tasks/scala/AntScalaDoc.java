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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import groovy.lang.Closure;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate;
import org.gradle.api.tasks.scala.ScalaDocOptions;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Ant-based Scaladoc.
 */
public class AntScalaDoc {
    private final IsolatedAntBuilder antBuilder;
    private final List<File> bootclasspathFiles;
    private final List<File> extensionDirs;

    public AntScalaDoc(IsolatedAntBuilder antBuilder) {
        this(antBuilder, ImmutableList.<File>of(), ImmutableList.<File>of());
    }

    public AntScalaDoc(IsolatedAntBuilder antBuilder, Iterable<File> bootclasspathFiles, Iterable<File> extensionDirs) {
        this.antBuilder = antBuilder;
        this.bootclasspathFiles = ImmutableList.copyOf(bootclasspathFiles);
        this.extensionDirs = ImmutableList.copyOf(extensionDirs);
    }

    public void execute(final FileCollection source, final File targetDir, final Iterable<File> classpathFiles, Iterable<File> scalaClasspath, final ScalaDocOptions docOptions) {
        antBuilder.withClasspath(scalaClasspath).execute(new Closure<Object>(this) {
            @SuppressWarnings("unused")
            public Object doCall(final AntBuilderDelegate ant) {
                ant.invokeMethod("taskdef", Collections.singletonMap("resource", "scala/tools/ant/antlib.xml"));
                ImmutableMap.Builder<String, Object> optionsBuilder = ImmutableMap.builder();
                optionsBuilder.put("destDir", targetDir);
                optionsBuilder.putAll(docOptions.optionMap());
                ImmutableMap<String, Object> options = optionsBuilder.build();

                return ant.invokeMethod("scaladoc", new Object[]{options, new Closure<Void>(this) {
                    public void doCall() {
                        source.addToAntBuilder(ant, "src", FileCollection.AntType.MatchingTask);
                        for (File file : bootclasspathFiles) {
                            ant.invokeMethod("bootclasspath", Collections.singletonMap("location", file));
                        }
                        for (File dir : extensionDirs) {
                            ant.invokeMethod("extdirs", Collections.singletonMap("location", dir));
                        }
                        for (File file : classpathFiles) {
                            ant.invokeMethod("classpath", Collections.singletonMap("location", file));
                        }
                    }
                }});
            }
        });
    }
}
