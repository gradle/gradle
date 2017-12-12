/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.tasks;

import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.Transformer;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.UncheckedException;
import org.gradle.nativeplatform.ModuleMap;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerConfiguration;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.gradle.util.CollectionUtils.collect;
import static org.gradle.util.CollectionUtils.filter;

/**
 * Generates a module map file for Swift interoperability with C/C++ projects.
 *
 * @since 4.5
 */
@Incubating
public class GenerateModuleMap extends DefaultTask {
    private final WorkerExecutor workerExecutor;
    private final RegularFileProperty moduleMapFile;
    private final ModuleMap moduleMap;

    @Inject
    public GenerateModuleMap(WorkerExecutor workerExecutor) {
        this.workerExecutor = workerExecutor;
        this.moduleMapFile = newOutputFile();
        this.moduleMap = new ModuleMap(getProject().getObjects().property(String.class), getProject().getObjects().listProperty(String.class));
    }

    /**
     * The location of the generated module map file.
     */
    @OutputFile
    public RegularFileProperty getModuleMapFile() {
        return moduleMapFile;
    }

    /**
     * The module map that will be used to generate the file.
     */
    @Nested
    public ModuleMap getModuleMap() {
        return moduleMap;
    }

    @TaskAction
    public void generateModuleMap() {
        workerExecutor.submit(GenerateModuleMapFile.class, new Action<WorkerConfiguration>() {
            @Override
            public void execute(WorkerConfiguration workerConfiguration) {
                workerConfiguration.setIsolationMode(IsolationMode.NONE);
                workerConfiguration.params(moduleMapFile.getAsFile().get(), moduleMap.getModuleName().get(), moduleMap.getPublicHeaderPaths().get());
            }
        });
    }

    private static class GenerateModuleMapFile implements Runnable {
        private final File moduleMapFile;
        private final String moduleName;
        private final List<String> publicHeaderDirs;

        @Inject
        public GenerateModuleMapFile(File moduleMapFile, String moduleName, List<String> publicHeaderDirs) {
            this.moduleMapFile = moduleMapFile;
            this.moduleName = moduleName;
            this.publicHeaderDirs = publicHeaderDirs;
        }

        @Override
        public void run() {
            List<String> lines = Lists.newArrayList(
                "module " + moduleName + " {"
            );
            List<String> validHeaderDirs = filter(publicHeaderDirs, new Spec<String>() {
                @Override
                public boolean isSatisfiedBy(String path) {
                    return new File(path).exists();
                }
            });
            lines.addAll(collect(validHeaderDirs, new Transformer<String, String>() {
                @Override
                public String transform(String path) {
                    return "\tumbrella \"" + path + "\"";
                }
            }));
            lines.add("\texport *");
            lines.add("}");
            try {
                FileUtils.writeLines(moduleMapFile, lines);
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }
}
