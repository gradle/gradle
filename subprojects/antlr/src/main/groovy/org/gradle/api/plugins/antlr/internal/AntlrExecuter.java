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
        // Try ANTLR 4
        try {
            final Antlr4Tool antlr4Tool = new Antlr4Tool();
            LOGGER.info("Processing with ANTLR 4");
            return antlr4Tool.process(spec);
        } catch (ClassNotFoundException e) {
            LOGGER.debug("ANTLR 4 not found on classpath");
        }

        // Try ANTLR 3
        try {
            final Antlr3Tool antlr3Tool = new Antlr3Tool();
            LOGGER.info("Processing with ANTLR 3");
            return antlr3Tool.process(spec);
        } catch (ClassNotFoundException e) {
            LOGGER.debug("ANTLR 3 not found on classpath");
        }

        // Try ANTLR 2
        try {
            final AntlrTool antlr2Tool = new Antlr2Tool();
            LOGGER.info("Processing with ANTLR 2");
            return antlr2Tool.process(spec);
        } catch (ClassNotFoundException e) {
            LOGGER.debug("ANTLR 2 not found on classpath");
        }

        throw new IllegalStateException("No Antlr implementation available");
    }

    private static class Antlr3Tool extends AntlrTool {
        public Antlr3Tool() throws ClassNotFoundException {
        }

        @Override
        int invoke(List<String> arguments) throws ClassNotFoundException {
            final Object backedObject = loadTool("org.antlr.Tool", null);
            String[] argArray = arguments.toArray(new String[arguments.size()]);
            if (inputDirectory != null) {
                JavaReflectionUtil.method(backedObject, Void.class, "setInputDirectory", String.class).invoke(backedObject, inputDirectory.getAbsolutePath());
                JavaReflectionUtil.method(backedObject, Void.class, "setForceRelativeOutput", boolean.class).invoke(backedObject, true);
            }
            JavaReflectionUtil.method(backedObject, Void.class, "processArgs", String[].class).invoke(backedObject, new Object[]{argArray});
            JavaReflectionUtil.method(backedObject, Void.class, "process").invoke(backedObject);
            return JavaReflectionUtil.method(backedObject, Integer.class, "getNumErrors").invoke(backedObject);
        }

        @Override
        public void checkAvailability() throws ClassNotFoundException {
            loadTool("org.antlr.Tool", null);
        }
    }

    private abstract static class AntlrTool {
        protected File inputDirectory;

        public AntlrTool() throws ClassNotFoundException {
            checkAvailability();
        }
        /**
         * Utility method to create an instance of the Tool class.
         *
         * @throws ClassNotFoundException if class was not on the runtime classpath.
         */
        static Object loadTool(String className, String[] args) throws ClassNotFoundException {
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

        /**
         * process used for antlr3/4
         * */
        public AntlrResult process(AntlrSpec spec) throws ClassNotFoundException {
            int numErrors = 0;
            if (spec.getInputDirectories().size() == 0) {
                // we have not root source folder information for the grammar files,
                // so we don't force relativeOutput as we can't calculate it.
                // This results in flat generated sources in the output directory
                numErrors += invoke(spec.asArgumentsWithFiles());
            } else {
                boolean onWindows = OperatingSystem.current().isWindows();
                for (File inputDirectory : spec.getInputDirectories()) {
                    final List<String> arguments = spec.getArguments();
                    arguments.add("-o");
                    arguments.add(spec.getOutputDirectory().getAbsolutePath());
                    for (File grammarFile : spec.getGrammarFiles()) {
                        String relativeGrammarFilePath = GFileUtils.relativePath(inputDirectory, grammarFile);
                        if (onWindows) {
                            relativeGrammarFilePath = relativeGrammarFilePath.replace('/', File.separatorChar);
                        }
                        arguments.add(relativeGrammarFilePath);
                    }
                    setInputDirectory(inputDirectory);
                    numErrors += invoke(arguments);
                }
            }
            return new AntlrResult(numErrors);
        }

        public void setInputDirectory(File inputDirectory) {
            this.inputDirectory = inputDirectory;
        }


        abstract int invoke(List<String> arguments) throws ClassNotFoundException;

        public abstract void checkAvailability() throws ClassNotFoundException;

        protected static String[] toArray(List<String> strings) {
            return strings.toArray(new String[strings.size()]);
        }

    }

    private static class Antlr4Tool extends AntlrTool{
        public Antlr4Tool() throws ClassNotFoundException {
        }

        @Override
        int invoke(List<String> arguments) throws ClassNotFoundException {
            final Object backedObject = loadTool("org.antlr.v4.Tool", toArray(arguments));
            if (inputDirectory != null) {
                JavaReflectionUtil.writeableField(backedObject.getClass(), "inputDirectory").setValue(backedObject, inputDirectory);
            }
            JavaReflectionUtil.method(backedObject, Void.class, "processGrammarsOnCommandLine").invoke(backedObject);
            return JavaReflectionUtil.method(backedObject, Integer.class, "getNumErrors").invoke(backedObject);
        }

        @Override
        public void checkAvailability() throws ClassNotFoundException {
            loadTool("org.antlr.v4.Tool", null);
        }
    }

    private class Antlr2Tool extends AntlrTool {
        public Antlr2Tool() throws ClassNotFoundException {
        }

        public AntlrResult process(AntlrSpec spec) throws ClassNotFoundException {
            XRef xref = new MetadataExtracter().extractMetadata(spec.getGrammarFiles());
            List<GenerationPlan> generationPlans = new GenerationPlanBuilder(spec.getOutputDirectory()).buildGenerationPlans(xref);
            for (GenerationPlan generationPlan : generationPlans) {
                List<String> generationPlanArguments = Lists.newArrayList(spec.getArguments());
                generationPlanArguments.add("-o");
                generationPlanArguments.add(generationPlan.getGenerationDirectory().getAbsolutePath());
                generationPlanArguments.add(generationPlan.getSource().getAbsolutePath());
                invoke(generationPlanArguments);
            }
            return new AntlrResult(0);  // ANTLR 2 always returning 0
        }

        @Override
        int invoke(List<String> arguments) throws ClassNotFoundException {
            final Object backedAntlrTool = loadTool("antlr.Tool", null);
            JavaReflectionUtil.method(backedAntlrTool, Integer.class, "doEverything", String[].class).invoke(backedAntlrTool, new Object[]{toArray(arguments)});
            return 0;
        }

        @Override
        public void checkAvailability() throws ClassNotFoundException {
            loadTool("antlr.Tool", null);
        }
    }
}
