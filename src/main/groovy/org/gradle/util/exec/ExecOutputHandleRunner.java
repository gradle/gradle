/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.util.exec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * @author Tom Eyckmans
 */
public class ExecOutputHandleRunner implements Runnable {
    private final static Logger logger = LoggerFactory.getLogger(ExecOutputHandleRunner.class);

    private final BufferedReader inputReader;
    private final ExecOutputHandle execOutputHandle;

    public ExecOutputHandleRunner(final InputStream inputStream, final ExecOutputHandle execOutputHandle) {
        this.inputReader = new BufferedReader(new InputStreamReader(inputStream));
        this.execOutputHandle = execOutputHandle;
    }

    public void run() {
        boolean keepHandling = true;

        try {
            String outputLine;
            while (keepHandling) {
                try {
                    outputLine = inputReader.readLine();
                } catch (Throwable t) {
                    keepHandling = execOutputHandle.execOutputHandleError(t);
                    continue;
                }

                if (outputLine == null) {
                    keepHandling = false;
                } else {
                    execOutputHandle.handleOutputLine(outputLine);
                }
            }
            execOutputHandle.endOutput();
        } catch (Throwable t) {
            logger.error("Could not process output from exec'd process.", t);
        }
    }
}
