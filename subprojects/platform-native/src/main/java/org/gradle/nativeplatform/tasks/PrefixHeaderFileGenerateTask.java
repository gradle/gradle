/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.nativeplatform.toolchain.internal.PCHUtils;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerConfiguration;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.File;

/**
 * Generates a prefix header file from a list of headers to be precompiled.
 */
@Incubating
public class PrefixHeaderFileGenerateTask extends DefaultTask {
    private String header;
    private File prefixHeaderFile;
    private final WorkerExecutor workerExecutor;

    /**
     * Injects a {@link WorkerExecutor} instance.
     *
     * @since 4.2
     */
    @Inject
    public PrefixHeaderFileGenerateTask(WorkerExecutor workerExecutor) {
        this.workerExecutor = workerExecutor;
    }

    @TaskAction
    void generatePrefixHeaderFile() {
        workerExecutor.submit(GeneratePrefixHeaderFile.class, new Action<WorkerConfiguration>() {
            @Override
            public void execute(WorkerConfiguration config) {
                config.setIsolationMode(IsolationMode.NONE);
                config.setParams(header, prefixHeaderFile);
            }
        });
    }

    @Input
    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    @OutputFile
    public File getPrefixHeaderFile() {
        return prefixHeaderFile;
    }

    public void setPrefixHeaderFile(File prefixHeaderFile) {
        this.prefixHeaderFile = prefixHeaderFile;
    }

    private static class GeneratePrefixHeaderFile implements Runnable {
        private final String header;
        private final File prefixHeaderFile;

        @Inject
        public GeneratePrefixHeaderFile(String header, File prefixHeaderFile) {
            this.header = header;
            this.prefixHeaderFile = prefixHeaderFile;
        }

        @Override
        public void run() {
            PCHUtils.generatePrefixHeaderFile(Lists.newArrayList(header), prefixHeaderFile);
        }
    }
}
