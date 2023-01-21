/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.jacoco;

import com.google.common.collect.ImmutableMap;
import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;

import java.util.Map;

public class AntJacocoInstrument {
    private final IsolatedAntBuilder ant;

    public AntJacocoInstrument(IsolatedAntBuilder ant) {
        this.ant = ant;
    }

    public void execute(final FileCollection classpath, final FileCollection inputClassesDirs, final Directory outputDir) {
        ant.withClasspath(classpath).execute(new Closure<Object>(this, this) {
            @SuppressWarnings({"UnusedDeclaration"})
            public Object doCall(Object it) {
                GroovyObjectSupport antBuilder = (GroovyObjectSupport) it;
                antBuilder.invokeMethod("taskdef", ImmutableMap.of(
                        "name", "jacocoInstrument",
                        "classname", "org.jacoco.ant.InstrumentTask"
                ));

                final Map<String, Object> instrumentArgs = ImmutableMap.of("destdir", outputDir.getAsFile());
                antBuilder.invokeMethod("jacocoInstrument", new Object[]{instrumentArgs, new Closure<Object>(this, this) {
                    @SuppressWarnings("UnusedDeclaration")
                    public Object doCall(Object ignore) {
                        inputClassesDirs.getAsFileTree().addToAntBuilder(antBuilder, "resources");
                        return null;
                    }
                }});
                return null;
            }
        });
    }
}
