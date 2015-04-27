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

package org.gradle.language.mirah.tasks;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Project;
import org.gradle.api.internal.tasks.mirah.DefaultMirahCompileSpec;
import org.gradle.api.internal.tasks.mirah.DefaultMirahCompileSpecFactory;
import org.gradle.api.internal.tasks.mirah.MirahCompileSpec;
import org.gradle.api.internal.tasks.mirah.MirahCompileSpec;
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
 * An abstract Mirah compile task sharing common functionality for compiling mirah.
 */
abstract public class AbstractMirahCompile extends AbstractCompile {
    protected static final Logger LOGGER = Logging.getLogger(AbstractMirahCompile.class);
    private final BaseMirahCompileOptions mirahCompileOptions;
    private final CompileOptions compileOptions = new CompileOptions();

    protected AbstractMirahCompile(BaseMirahCompileOptions mirahCompileOptions) {
        this.mirahCompileOptions = mirahCompileOptions;
    }

    /**
     * Returns the Mirah compilation options.
     */
    @Nested
    public BaseMirahCompileOptions getMirahCompileOptions() {
        return mirahCompileOptions;
    }

    /**
     * Returns the Java compilation options.
     */
    @Nested
    public CompileOptions getOptions() {
        return compileOptions;
    }

    abstract protected org.gradle.language.base.internal.compile.Compiler<MirahCompileSpec> getCompiler(MirahCompileSpec spec);

    @TaskAction
    protected void compile() {
        MirahCompileSpec spec = createSpec();
        configureIncrementalCompilation(spec);
        getCompiler(spec).execute(spec);
    }

    protected MirahCompileSpec createSpec() {
        DefaultMirahCompileSpec spec = new DefaultMirahCompileSpecFactory(compileOptions).create();
        spec.setSource(getSource());
        spec.setDestinationDir(getDestinationDir());
        spec.setWorkingDir(getProject().getProjectDir());
        spec.setTempDir(getTemporaryDir());
        spec.setClasspath(getClasspath());
        spec.setSourceCompatibility(getSourceCompatibility());
        spec.setTargetCompatibility(getTargetCompatibility());
        spec.setCompileOptions(getOptions());
        spec.setMirahCompileOptions(mirahCompileOptions);
        return spec;
    }

    protected void configureIncrementalCompilation(MirahCompileSpec spec) {

        Map<File, File> globalAnalysisMap = getOrCreateGlobalAnalysisMap();
        HashMap<File, File> filteredMap = filterForClasspath(globalAnalysisMap, spec.getClasspath());
        spec.setAnalysisMap(filteredMap);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Analysis file: {}", mirahCompileOptions.getIncrementalOptions().getAnalysisFile());
            LOGGER.debug("Published code: {}", mirahCompileOptions.getIncrementalOptions().getPublishedCode());
            LOGGER.debug("Analysis map: {}", filteredMap);
        }
    }

    @SuppressWarnings("unchecked")
    protected Map<File, File> getOrCreateGlobalAnalysisMap() {
        ExtraPropertiesExtension extraProperties = getProject().getRootProject().getExtensions().getExtraProperties();
        Map<File, File> analysisMap;

        if (extraProperties.has("mirahCompileAnalysisMap")) {
            analysisMap = (Map) extraProperties.get("mirahCompileAnalysisMap");
        } else {
            analysisMap = Maps.newHashMap();
            for (Project project : getProject().getRootProject().getAllprojects()) {
                for (AbstractMirahCompile task : project.getTasks().withType(AbstractMirahCompile.class)) {
                    File publishedCode = task.getMirahCompileOptions().getIncrementalOptions().getPublishedCode();
                    File analysisFile = task.getMirahCompileOptions().getIncrementalOptions().getAnalysisFile();
                    analysisMap.put(publishedCode, analysisFile);
                }
            }
            extraProperties.set("mirahCompileAnalysisMap", Collections.unmodifiableMap(analysisMap));
        }
        return analysisMap;
    }


    protected HashMap<File, File> filterForClasspath(Map<File, File> analysisMap, Iterable<File> classpath) {
        final Set<File> classpathLookup = Sets.newHashSet(classpath);
        return Maps.newHashMap(Maps.filterEntries(analysisMap, new Predicate<Map.Entry<File, File>>() {
            public boolean apply(Map.Entry<File, File> entry) {
                return classpathLookup.contains(entry.getKey());
            }
        }));
    }
}
