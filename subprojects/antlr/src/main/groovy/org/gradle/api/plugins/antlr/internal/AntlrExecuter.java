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

import org.gradle.api.GradleException;
import org.gradle.api.plugins.antlr.internal.antlr2.GenerationPlan;
import org.gradle.api.plugins.antlr.internal.antlr2.GenerationPlanBuilder;
import org.gradle.api.plugins.antlr.internal.antlr2.MetadataExtracter;
import org.gradle.api.plugins.antlr.internal.antlr2.XRef;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;

public class AntlrExecuter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AntlrExecuter.class);

    AntlrResult runAntlr(AntlrSpec spec) throws IOException, InterruptedException {
        String[] commandLine = toArray(spec.asCommandLineWithFiles());

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
            Object toolObj = loadTool("org.antlr.Tool", commandLine);
            LOGGER.info("Processing with ANTLR 3");
            return processV3(toolObj);
        } catch (ClassNotFoundException e) {
            LOGGER.debug("ANTLR 3 not found on classpath");
        }

        // Try ANTLR 2
        try {
            Object toolObj = loadTool("antlr.Tool", null);
            LOGGER.info("Processing with ANTLR 2");
            return processV2(toolObj, spec.asCommandLineWithoutFiles(), spec.getGrammarFiles(), spec.getOutputDirectory());
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

    AntlrResult processV2(Object tool, List<String> arguments, Set<File> grammarFiles, File outputDirectory) {
        XRef xref = new MetadataExtracter().extractMetadata(grammarFiles);
        List<GenerationPlan> generationPlans = new GenerationPlanBuilder(outputDirectory).buildGenerationPlans(xref);
        for (GenerationPlan generationPlan : generationPlans) {
            String[] argArr = arguments.toArray(new String[arguments.size() + 1]);
            argArr[arguments.size()] = generationPlan.getSource().getAbsolutePath();
            JavaReflectionUtil.method(tool, Integer.class, "doEverything", String[].class).invoke(tool, new Object[]{argArr});
        }
        return new AntlrResult(0);  // ANTLR 2 always returning 0
    }

    AntlrResult processV3(Object tool) {
        JavaReflectionUtil.method(tool, Void.class, "process").invoke(tool);
        return new AntlrResult(JavaReflectionUtil.method(tool, Integer.class, "getNumErrors").invoke(tool));
    }

    AntlrResult processV4(Object tool) {
        JavaReflectionUtil.method(tool, Void.class, "processGrammarsOnCommandLine").invoke(tool);
        return new AntlrResult(JavaReflectionUtil.method(tool, Integer.class, "getNumErrors").invoke(tool));
    }

}