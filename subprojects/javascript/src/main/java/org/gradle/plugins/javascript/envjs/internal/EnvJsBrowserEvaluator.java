/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.plugins.javascript.envjs.internal;

import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.Factory;
import org.gradle.plugins.javascript.envjs.browser.BrowserEvaluator;
import org.gradle.plugins.javascript.rhino.worker.internal.RhinoWorkerHandleFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;

public class EnvJsBrowserEvaluator implements BrowserEvaluator {

    private final RhinoWorkerHandleFactory rhinoWorkerHandleFactory;
    private final Iterable<File> rhinoClasspath;
    private final LogLevel logLevel;
    private final File workingDir;
    private final Factory<File> envJsFactory;

    public EnvJsBrowserEvaluator(RhinoWorkerHandleFactory rhinoWorkerHandleFactory, Iterable<File> rhinoClasspath, Factory<File> envJsFactory, LogLevel logLevel, File workingDir) {
        this.rhinoWorkerHandleFactory = rhinoWorkerHandleFactory;
        this.rhinoClasspath = rhinoClasspath;
        this.envJsFactory = envJsFactory;
        this.logLevel = logLevel;
        this.workingDir = workingDir;
    }

    @Override
    public void evaluate(String url, Writer writer) {
        EnvJvEvaluateProtocol evaluator = rhinoWorkerHandleFactory.create(rhinoClasspath, EnvJvEvaluateProtocol.class, EnvJsEvaluateWorker.class, logLevel, workingDir);

        final String result = evaluator.process(new EnvJsEvaluateSpec(envJsFactory.create(), url));

        try {
            IOUtils.copy(new StringReader(result), writer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
