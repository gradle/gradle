/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.logging.Logging;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.remote.ObjectConnection;
import org.gradle.process.internal.worker.WorkerMessageSerializer;
import org.gradle.process.internal.worker.WorkerProcessContext;
import org.gradle.process.internal.worker.child.WorkerLogEventListener;
import org.gradle.process.internal.worker.child.WorkerLoggingProtocol;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.nio.charset.Charset;

public class LoggingProcess implements Action<WorkerProcessContext>, Serializable {
    @Override
    public void execute(WorkerProcessContext workerProcessContext) {
        File file = new File("/Users/Rene/Desktop/debugoutput.txt");
        try {
            configureLogging(createLoggingManager(), workerProcessContext.getServerConnection());

            try {
                RuntimeException runtimeException = new RuntimeException("blubb");
                throw runtimeException;
            } catch (RuntimeException e) {
                try {
                    FileUtils.write(file, e.getMessage(), true);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                try {
                    e.printStackTrace(new PrintWriter(Files.newWriter(file, Charset.defaultCharset())));
                } catch (FileNotFoundException e1) {
                    e1.printStackTrace();
                }
            }
            Logging.getLogger(getClass()).error("blubb");
//        Logging.getLogger(getClass()).debug("debug message");
//        Logging.getLogger(getClass()).debug("debug message");
            System.out.println("this is stdout");
            System.err.println("this is stderr");
        } catch (RuntimeException r) {
            try {
                FileUtils.write(file, "ERROR: " + r.getMessage(), true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void configureLogging(LoggingManagerInternal loggingManager, ObjectConnection connection) {
        connection.useParameterSerializers(WorkerMessageSerializer.create());
        WorkerLoggingProtocol workerLoggingProtocol = connection.addOutgoing(WorkerLoggingProtocol.class);
        loggingManager.addOutputEventListener(new WorkerLogEventListener(workerLoggingProtocol));
    }

    LoggingManagerInternal createLoggingManager() {
        LoggingManagerInternal loggingManagerInternal = LoggingServiceRegistry.newEmbeddableLogging().newInstance(LoggingManagerInternal.class);
        loggingManagerInternal.captureSystemSources();
        return loggingManagerInternal;
    }


}
