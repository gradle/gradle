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

package org.gradle.api.plugins.quality.internal.findbugs;

import org.gradle.api.Action;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.process.internal.WorkerProcessContext;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class FindBugsDaemonServer implements Action<WorkerProcessContext>, Serializable {
    private static final Logger LOGGER = Logging.getLogger(FindBugsDaemon.class);

    private volatile CountDownLatch stop;
    private FindBugsSpec spec;

    public FindBugsDaemonServer(FindBugsSpec spec){
        this.spec = spec;
    }

    public void execute(WorkerProcessContext context) {
        final FindBugsResult result = execute();
        final FindBugsDaemonClientProtocol clientProtocol = context.getServerConnection().addOutgoing(FindBugsDaemonClientProtocol.class);
        clientProtocol.executed(result);
    }

    public FindBugsResult execute() {
        try {
            LOGGER.info("Executing findbugs daemon.");
            LOGGER.debug("Running findbugs specification {}", spec);
            String findbugsOutput = runFindbugs(spec);
            LOGGER.debug(findbugsOutput);
            LOGGER.info("Successfully executed in findbugs daemon.");
            return createFindBugsResultFromOutput(findbugsOutput);
        } catch (Throwable t) {
            LOGGER.info("Exception executing {} in findbugs daemon:", t);
        }
        return new FindBugsResult(true); //TODO RG: handle errors in findbugs.
    }

    private FindBugsResult createFindBugsResultFromOutput(String findbugsOutput) {
        if (findbugsOutput.contains("Warnings generated: ")) {
            return new FindBugsResult(true);
        } else {
            return new FindBugsResult(false);
        }
    }

    private String runFindbugs(FindBugsSpec spec) throws Exception {
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        final PrintStream origOut = System.out;
        final PrintStream origErr = System.err;

        try {

            final List<String> args = spec.getArguments();
            String[] strArray = new String[args.size()];
            args.toArray(strArray);
            final Class<?> findbugs2 = getClass().getClassLoader().loadClass("edu.umd.cs.findbugs.FindBugs2");
            final Method findbugsMain = findbugs2.getMethod("main", strArray.getClass());

            // TODO RG: replace ByteArrayOutputStream by OutputStream that handles logging directly.
            // TODO RG: use seperate streams for out and err.
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setOut(new PrintStream(baos));
            System.setErr(new PrintStream(baos));

            System.setProperty("findbugs.debug", Boolean.toString(spec.isDebugEnabled()));
            Thread.currentThread().setContextClassLoader(findbugs2.getClassLoader());
            findbugsMain.invoke(null, new Object[]{strArray});
            return baos.toString();
        } finally {
            System.setOut(origOut);
            System.setErr(origErr);
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        }
    }
}
