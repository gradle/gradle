/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.daemon;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.Stoppable;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.WorkerProcess;
import org.gradle.process.internal.WorkerProcessBuilder;

public class CompilerDaemonManager implements Stoppable {
    private static final CompilerDaemonManager INSTANCE = new CompilerDaemonManager();
    
    private DefaultCompilerDaemon daemon;
    private WorkerProcess process;
    
    public static CompilerDaemonManager getInstance() {
        return INSTANCE;
    }
    
    public CompilerDaemon getDaemon(ProjectInternal project) {
        if (daemon == null) {
            startDaemon(project);
            registerShutdownListener(project);
        }
        return daemon;
    }
    
    public void stop() {
        process.waitForStop();
    }
    
    private void startDaemon(ProjectInternal project) {
        WorkerProcessBuilder builder = project.getServices().getFactory(WorkerProcessBuilder.class).create();
        JavaExecHandleBuilder javaCommand = builder.getJavaCommand();
        javaCommand.setMinHeapSize("128m");
        javaCommand.setMaxHeapSize("1g");
        javaCommand.setWorkingDir(project.getRootProject().getProjectDir());
        daemon = new DefaultCompilerDaemon();
        process = builder.worker(daemon).build();
        process.start();
    }
    
    private void registerShutdownListener(ProjectInternal project) {
        project.getGradle().addBuildListener(new BuildAdapter() {
            @Override
            public void buildFinished(BuildResult result) {
                stop();
            }
        });
    }
}
