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

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.gradle.api.GradleException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AntlrExecuter implements Serializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AntlrExecuter.class);

    public AntlrExecuter() {
    }

    AntlrResult runAntlr(AntlrSpec spec) throws IOException, InterruptedException {
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            final List<String> args = spec.getArguments();
            String[] argArr = new String[args.size()];
            args.toArray(argArr);
            Thread.currentThread().setContextClassLoader(antlr.Tool.class.getClassLoader());

            // Try ANTLR 4
            try {
                Object toolObj = loadTool("org.antlr.v4.Tool", argArr);
                org.antlr.v4.Tool tool = (org.antlr.v4.Tool) toolObj;
                LOGGER.info("Processing with ANTLR 4");
                return process(tool);
            } catch (ClassNotFoundException e) {
                LOGGER.debug("ANTLR 4 not found on classpath");
            }

            // Try ANTLR 3
            try {
                Object toolObj = loadTool("org.antlr.Tool", argArr);
                org.antlr.Tool tool = (org.antlr.Tool) toolObj;
                LOGGER.info("Processing with ANTLR 3");
                return process(tool);
            } catch (ClassNotFoundException e) {
                LOGGER.debug("ANTLR 3 not found on classpath");
            }

            // Use ANTLR 2 - compile time version
            antlr.Tool tool = new antlr.Tool();
            LOGGER.info("Processing with ANTLR 2");
            return process(tool, argArr);
        } finally {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        }
    }

    /**
     * Utility method to create an instance of the Tool class.
     * @throws ClassNotFoundException if class was not on the runtime classpath.
     */
    Object loadTool(String className, String[] args) throws ClassNotFoundException {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> toolClass = Class.forName(className, true, cl);
            Constructor<?> constructor = toolClass.getConstructor(String[].class);
            Object tool = constructor.newInstance(new Object[] {args});
            return tool;
        } catch (NoSuchMethodException e) {
            throw new GradleException("Failed to load ANTLR", e);
        } catch (InstantiationException e) {
            throw new GradleException("Failed to load ANTLR", e);
        } catch (IllegalAccessException e) {
            throw new GradleException("Failed to load ANTLR", e);
        } catch (InvocationTargetException e) {
            throw new GradleException("Failed to load ANTLR", e);
        }
    }

    /**
     * Process with ANTLR 2.
     */
    AntlrResult process(antlr.Tool tool, String[] args) {
        tool.doEverything(args);
        return new AntlrResult(1); // Failsafe - ANTLR 2 calls System.exit() on error
    }

    /**
     * Process with ANTLR 3.
     */
    AntlrResult process(org.antlr.Tool tool) {
        tool.process();
        return new AntlrResult(tool.getNumErrors());
    }

    /**
     * Process with ANTLR 4.
     */
    AntlrResult process(org.antlr.v4.Tool tool) {
        tool.processGrammarsOnCommandLine();
        return new AntlrResult(tool.getNumErrors());
    }

    /**
     * Create ANTLR 3 result.
     */
    AntlrResult createAntlrResult(org.antlr.Tool tool) {
        return new AntlrResult(tool.getNumErrors());
    }

    /**
     * Create ANTLR 4 result.
     */
    AntlrResult createAntlrResult(org.antlr.v4.Tool tool) {
        return new AntlrResult(tool.getNumErrors());
    }
}