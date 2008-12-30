/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.api.tasks.diagnostics;

import org.gradle.api.internal.DefaultTask;
import org.gradle.api.internal.dependencies.BaseDependencyManager;
import org.gradle.api.internal.dependencies.DefaultDependencyResolver;
import org.gradle.api.Project;
import org.gradle.api.TaskAction;
import org.gradle.api.Task;
import org.gradle.api.GradleException;
import org.gradle.api.dependencies.report.IvyDependencyGraphBuilder;
import org.gradle.api.dependencies.report.IvyDependencyGraph;

import java.util.List;
import java.io.File;
import java.io.OutputStream;
import java.io.FileOutputStream;

/**
 * Task to show a dependency tree for a project. Can be configured to output to a file,
 * and to optionally output a graphviz compatible "dot" graph.
 *
 * @author Phil Messenger
 */
public class ShowDependenciesTask extends DefaultTask
{

    DependencyGraphRenderer renderer = new AsciiGraphRenderer();

    File outputFile;

    String conf = "runtime";

    /**
     * Set the configuration to build a dependency report for
     *
     * @param conf
     */
    public void setConf(String conf)
    {
        this.conf = conf;
    }

    /**
     * Set the outputfile to write the dependency report to. If unset,
     * standard out will be used
     *
     * @param outputFile
     */
    public void setOutputFile(File outputFile)
    {
        this.outputFile = outputFile;
    }

    /**
     * Set the renderer to use to build a report. If unset, AsciiGraphRenderer will be used.
     * 
     * @param renderer
     */
    public void setRenderer(DependencyGraphRenderer renderer)
    {
        this.renderer = renderer;
    }

    public ShowDependenciesTask(Project project, String name) {
        super(project, name);
        setDagNeutral(true);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                Project project = task.getProject();

                IvyDependencyGraphBuilder graphBuilder = new IvyDependencyGraphBuilder();

                BaseDependencyManager depManager = (BaseDependencyManager) project.getDependencies();

                depManager.resolve(conf, false, true);

                DefaultDependencyResolver resolver = (DefaultDependencyResolver) depManager.getDependencyResolver();

                try {
                    IvyDependencyGraph graph = graphBuilder.buildGraph(project, resolver.getLastResolveReport(), conf);

                    OutputStream outputStream = System.out;

                    if(outputFile != null)
                    {
                        outputStream = new FileOutputStream(outputFile);
                    }
                    try
                    {
                        renderer.render(graph, System.out);
                    }
                    finally
                    {
                        if(outputFile != null)
                        {
                            outputStream.close();
                        }
                    }
                } catch (Exception e) {
                    throw new GradleException(e);
                }
            }
        });
    }

}
