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

package org.gradle.plugins.javascript.envjs.browser;

import org.gradle.api.DefaultTask;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.tasks.*;
import org.gradle.plugins.javascript.envjs.http.HttpFileServer;
import org.gradle.plugins.javascript.envjs.http.simple.SimpleHttpFileServerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.Callable;

public class BrowserEvaluate extends DefaultTask {

    private Object content;
    private Object resource;
    private BrowserEvaluator evaluator;
    private Object result;

    public BrowserEvaluate() {
        dependsOn(new Callable<TaskDependency>() {
            public TaskDependency call() throws Exception {
                return getProject().files(BrowserEvaluate.this.content).getBuildDependencies();
            }
        });
    }

    @InputDirectory
    public File getContent() {
        return content == null ? null : getProject().files(content).getSingleFile();
    }

    public void setContent(Object content) {
        this.content = content;
    }

    @Input
    public String getResource() {
        return resource.toString();
    }

    public void setResource(Object resource) {
        this.resource = resource;
    }

    //@Input
    public BrowserEvaluator getEvaluator() {
        return evaluator;
    }

    public void setEvaluator(BrowserEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @OutputFile
    public File getResult() {
        return result == null ? null : getProject().file(result);
    }

    public void setResult(Object result) {
        this.result = result;
    }

    @TaskAction
    void doEvaluate() {
        HttpFileServer fileServer = new SimpleHttpFileServerFactory().start(getContent(), 0);

        try {
            Writer resultWriter = new FileWriter(getResult());
            getEvaluator().evaluate(fileServer.getResourceUrl(getResource()), resultWriter);
            resultWriter.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            fileServer.stop();
        }

        setDidWork(true);
    }
}
