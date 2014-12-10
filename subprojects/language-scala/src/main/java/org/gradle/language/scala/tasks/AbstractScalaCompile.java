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

package org.gradle.language.scala.tasks;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Project;
import org.gradle.api.internal.tasks.scala.AbstractScalaJavaJointCompileSpec;
import org.gradle.api.internal.tasks.scala.PlatformScalaCompileSpec;
import org.gradle.api.internal.tasks.scala.PlatformScalaJavaJointCompileSpec;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * An abstract Scala compile task sharing common functionality for compiling scala.
 * @param <S> the CompileSpec type being used by the compiler.
 * @param <T> the CompileOptions type being used by the compiler.
 */
abstract public class AbstractScalaCompile<S extends PlatformScalaJavaJointCompileSpec, T extends PlatformScalaCompileOptions> extends AbstractCompile {
    protected static final Logger LOGGER = Logging.getLogger(AbstractScalaCompile.class);
    private final CompileOptions compileOptions = new CompileOptions();

    /**
     * Returns the Scala compilation options.
     */
    abstract public T getScalaCompileOptions();
    abstract protected AbstractScalaJavaJointCompileSpec<T> newSpec();

    /**
     * Returns the Java compilation options.
     */
    @Nested
    public CompileOptions getOptions() {
        return compileOptions;
    }

    abstract protected org.gradle.language.base.internal.compile.Compiler<S> getCompiler(S spec);

    @TaskAction
    protected void compile() {
        S spec = createSpec();
        spec.setSource(getSource());
        spec.setDestinationDir(getDestinationDir());
        spec.setWorkingDir(getProject().getProjectDir());
        spec.setTempDir(getTemporaryDir());
        spec.setClasspath(getClasspath());
        spec.setSourceCompatibility(getSourceCompatibility());
        spec.setTargetCompatibility(getTargetCompatibility());
        if (!useAnt()) {
            configureIncrementalCompilation(spec);
        }
        getCompiler(spec).execute(spec);
    }

    protected S createSpec() {
        AbstractScalaJavaJointCompileSpec<T> spec = newSpec();
        spec.setSource(getSource());
        spec.setDestinationDir(getDestinationDir());
        spec.setWorkingDir(getProject().getProjectDir());
        spec.setTempDir(getTemporaryDir());
        spec.setClasspath(getClasspath());
        spec.setSourceCompatibility(getSourceCompatibility());
        spec.setTargetCompatibility(getTargetCompatibility());
        spec.setCompileOptions(getOptions());
        spec.setScalaCompileOptions(getScalaCompileOptions());
        @SuppressWarnings("unchecked") S returnSpec = (S)spec;
        return returnSpec;
    }

    @SuppressWarnings("unchecked")
    protected Map<File, File> getOrCreateGlobalAnalysisMap() {
        ExtraPropertiesExtension extraProperties = getProject().getRootProject().getExtensions().getExtraProperties();
        Map<File, File> analysisMap;

        if (extraProperties.has("scalaCompileAnalysisMap")) {
            analysisMap = (Map) extraProperties.get("scalaCompileAnalysisMap");
        } else {
            analysisMap = Maps.newHashMap();
            for (Project project : getProject().getRootProject().getAllprojects()) {
                for (AbstractScalaCompile<S, T> task : project.getTasks().withType(getClass())) {
                    if(useAnt()){
                        continue;
                    }
                    File publishedCode = task.getScalaCompileOptions().getIncrementalOptions().getPublishedCode();
                    File analysisFile = task.getScalaCompileOptions().getIncrementalOptions().getAnalysisFile();
                    analysisMap.put(publishedCode, analysisFile);
                }
            }
            extraProperties.set("scalaCompileAnalysisMap", Collections.unmodifiableMap(analysisMap));
        }
        return analysisMap;
    }

    abstract protected boolean useAnt();



    protected HashMap<File, File> filterForClasspath(Map<File, File> analysisMap, Iterable<File> classpath) {
        final Set<File> classpathLookup = Sets.newHashSet(classpath);
        return Maps.newHashMap(Maps.filterEntries(analysisMap, new Predicate<Map.Entry<File, File>>() {
            public boolean apply(Map.Entry<File, File> entry) {
                return classpathLookup.contains(entry.getKey());
            }
        }));
    }


    protected void configureIncrementalCompilation(PlatformScalaCompileSpec spec) {
        Map<File, File> globalAnalysisMap = getOrCreateGlobalAnalysisMap();
        HashMap<File, File> filteredMap = filterForClasspath(globalAnalysisMap, spec.getClasspath());
        spec.setAnalysisMap(filteredMap);
        if (LOGGER.isDebugEnabled()) {
            T scalaCompileOptions = getScalaCompileOptions();
            LOGGER.debug("Analysis file: {}", scalaCompileOptions.getIncrementalOptions().getAnalysisFile());
            LOGGER.debug("Published code: {}", scalaCompileOptions.getIncrementalOptions().getPublishedCode());
            LOGGER.debug("Analysis map: {}", filteredMap);
        }
    }
}
