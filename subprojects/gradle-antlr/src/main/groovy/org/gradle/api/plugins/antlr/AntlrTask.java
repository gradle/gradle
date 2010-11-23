/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.plugins.antlr;

import java.io.File;
import java.util.List;

import org.apache.tools.ant.taskdefs.optional.ANTLR;
import org.apache.tools.ant.types.Path;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.antlr.internal.GenerationPlan;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.plugins.antlr.internal.MetadataExtracter;
import org.gradle.api.plugins.antlr.internal.XRef;
import org.gradle.api.plugins.antlr.internal.GenerationPlanBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Generates parsers from Antlr grammars.</p>
 *
 * <p>Most properties here are self-evident, but I wanted to highlight one in particular: {@link #setAntlrClasspath} is
 * used to define the classpath that should be passed along to the Ant {@link ANTLR} task as its classpath.  That is the
 * classpath it uses to perform generation execution.  This <b>should</b> really only require the antlr jar.  In {@link
 * AntlrPlugin} usage, this would happen simply by adding your antlr jar into the 'antlr' dependency configuration
 * created and exposed by the {@link AntlrPlugin} itself.</p>
 *
 * @author Steve Ebersole
 */
public class AntlrTask extends SourceTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(AntlrTask.class);

    private boolean trace;
    private boolean traceLexer;
    private boolean traceParser;
    private boolean traceTreeWalker;

    private FileCollection antlrClasspath;

    private File outputDirectory;

    public boolean isTrace() {
        return trace;
    }

    public void setTrace(boolean trace) {
        this.trace = trace;
    }

    public boolean isTraceLexer() {
        return traceLexer;
    }

    public void setTraceLexer(boolean traceLexer) {
        this.traceLexer = traceLexer;
    }

    public boolean isTraceParser() {
        return traceParser;
    }

    public void setTraceParser(boolean traceParser) {
        this.traceParser = traceParser;
    }

    public boolean isTraceTreeWalker() {
        return traceTreeWalker;
    }

    public void setTraceTreeWalker(boolean traceTreeWalker) {
        this.traceTreeWalker = traceTreeWalker;
    }

    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @InputFiles
    public FileCollection getAntlrClasspath() {
        return antlrClasspath;
    }

    public void setAntlrClasspath(FileCollection antlrClasspath) {
        this.antlrClasspath = antlrClasspath;
    }

    @TaskAction
    public void generate() {
        // Determine the grammar files and the proper ordering amongst them
        XRef xref = new MetadataExtracter().extractMetadata(getSource());
        List<GenerationPlan> generationPlans = new GenerationPlanBuilder(outputDirectory).buildGenerationPlans(xref);

        for (GenerationPlan generationPlan : generationPlans) {
            if (!generationPlan.isOutOfDate()) {
                LOGGER.info("grammar [" + generationPlan.getId() + "] was up-to-date; skipping");
                continue;
            }

            LOGGER.info("performing grammar generation [" + generationPlan.getId() + "]");

            //noinspection ResultOfMethodCallIgnored
            generationPlan.getGenerationDirectory().mkdirs();

            ANTLR antlr = new ANTLR();
            antlr.setProject(getAnt().getAntProject());
            Path antlrTaskClasspath = antlr.createClasspath();
            for (File dep : getAntlrClasspath()) {
                antlrTaskClasspath.createPathElement().setLocation(dep);
            }
            antlr.setTrace(trace);
            antlr.setTraceLexer(traceLexer);
            antlr.setTraceParser(traceParser);
            antlr.setTraceTreeWalker(traceTreeWalker);
            antlr.setOutputdirectory(generationPlan.getGenerationDirectory());
            antlr.setTarget(generationPlan.getSource());

            antlr.execute();
        }
    }
}
