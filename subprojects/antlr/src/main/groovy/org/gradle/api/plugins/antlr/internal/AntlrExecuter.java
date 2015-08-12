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

package org.gradle.api.plugins.antlr.internal;

import com.beust.jcommander.internal.Lists;
import org.gradle.api.GradleException;
import org.gradle.api.plugins.antlr.internal.antlr2.GenerationPlan;
import org.gradle.api.plugins.antlr.internal.antlr2.GenerationPlanBuilder;
import org.gradle.api.plugins.antlr.internal.antlr2.MetadataExtracter;
import org.gradle.api.plugins.antlr.internal.antlr2.XRef;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.util.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class AntlrExecuter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AntlrExecuter.class);

    AntlrResult runAntlr(AntlrSpec spec) throws IOException, InterruptedException {
        String[] commandLine = toArray(spec.asArgumentsWithFiles());

        // Try ANTLR 4
        try {
            Object toolObj = loadTool("org.antlr.v4.Tool", commandLine);
            LOGGER.info("Processing with ANTLR 4");
            return processV4(toolObj);
        } catch (ClassNotFoundException e) {
            LOGGER.debug("ANTLR 4 not found on classpath");
        }

        // Try ANTLR 3
        try {
            // check if antlr3 available
            loadTool("org.antlr.Tool", null);
            LOGGER.info("Processing with ANTLR 3");
            return processV3(spec);
        } catch (ClassNotFoundException e) {
            LOGGER.debug("ANTLR 3 not found on classpath");
        }

        // Try ANTLR 2
        try {
            Object toolObj = loadTool("antlr.Tool", null);
            LOGGER.info("Processing with ANTLR 2");
            return processV2(toolObj, spec);
        } catch (ClassNotFoundException e) {
            LOGGER.debug("ANTLR 2 not found on classpath");
        }

        throw new IllegalStateException("No Antlr implementation available");
    }

    private String[] toArray(List<String> strings) {
        return strings.toArray(new String[strings.size()]);
    }

    /**
     * Utility method to create an instance of the Tool class.
     *
     * @throws ClassNotFoundException if class was not on the runtime classpath.
     */
    Object loadTool(String className, String[] args) throws ClassNotFoundException {
        try {
            Class<?> toolClass = Class.forName(className); // ok to use caller classloader
            if (args == null) {
                return toolClass.newInstance();
            } else {
                Constructor<?> constructor = toolClass.getConstructor(String[].class);
                return constructor.newInstance(new Object[]{args});
            }
        } catch (ClassNotFoundException e) {
            throw e;
        } catch (InvocationTargetException e) {
            throw new GradleException("Failed to load ANTLR", e.getCause());
        } catch (Exception e) {
            throw new GradleException("Failed to load ANTLR", e);
        }
    }

    AntlrResult processV2(Object tool, AntlrSpec spec) {
        XRef xref = new MetadataExtracter().extractMetadata(spec.getGrammarFiles());
        List<GenerationPlan> generationPlans = new GenerationPlanBuilder(spec.getOutputDirectory()).buildGenerationPlans(xref);
        for (GenerationPlan generationPlan : generationPlans) {
            List<String> generationPlanArguments = Lists.newArrayList(spec.getArguments());
            generationPlanArguments.add("-o");
            generationPlanArguments.add(generationPlan.getGenerationDirectory().getAbsolutePath());
            generationPlanArguments.add(generationPlan.getSource().getAbsolutePath());
            String[] argArr = generationPlanArguments.toArray(new String[generationPlanArguments.size()]);
            JavaReflectionUtil.method(tool, Integer.class, "doEverything", String[].class).invoke(tool, new Object[]{argArr});
        }
        return new AntlrResult(0);  // ANTLR 2 always returning 0
    }

    AntlrResult processV3(AntlrSpec spec) throws ClassNotFoundException {
        int numErrors = 0;
        if (spec.getInputDirectories().size() == 0) {
            // we have not root source folder information for the grammar files,
            // so we don't force relativeOutput as we can't calculate it.
            // This results in flat generated sources in the output directory
            numErrors += invokeV3ToolingAndReturnErrorNumber(spec.asArgumentsWithFiles(), null);
        } else {
            boolean onWindows = OperatingSystem.current().isWindows();
            for (File inputDirectory : spec.getInputDirectories()) {
                final List<String> arguments = spec.getArguments();
                arguments.add("-o");
                arguments.add(spec.getOutputDirectory().getAbsolutePath());
                for (File grammarFile : spec.getGrammarFiles()) {
                    String relativeGrammarFilePath = GFileUtils.relativePath(inputDirectory, grammarFile);
                    if(onWindows){
                        relativeGrammarFilePath = relativeGrammarFilePath.replace('/', File.separatorChar);
                    }
                    arguments.add(relativeGrammarFilePath);
                }
                numErrors += invokeV3ToolingAndReturnErrorNumber(arguments, inputDirectory);
            }
        }
        return new AntlrResult(numErrors);
    }

    private int invokeV3ToolingAndReturnErrorNumber(List<String> arguments, File inputDirectory) throws ClassNotFoundException {
        String[] argArray = arguments.toArray(new String[arguments.size()]);
        Object tool = loadTool("org.antlr.Tool", argArray);
        if (inputDirectory != null) {
            JavaReflectionUtil.method(tool, Void.class, "setInputDirectory", String.class).invoke(tool, inputDirectory.getAbsolutePath());
            JavaReflectionUtil.method(tool, Void.class, "setForceRelativeOutput", boolean.class).invoke(tool, true);
        }
        JavaReflectionUtil.method(tool, Void.class, "process").invoke(tool);
        return JavaReflectionUtil.method(tool, Integer.class, "getNumErrors").invoke(tool);
    }

    AntlrResult processV4(Object tool) {
        JavaReflectionUtil.method(tool, Void.class, "processGrammarsOnCommandLine").invoke(tool);
        return new AntlrResult(JavaReflectionUtil.method(tool, Integer.class, "getNumErrors").invoke(tool));
    }
}
