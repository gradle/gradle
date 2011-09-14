/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.server.exec;

import org.gradle.StartParameter;
import org.gradle.BuildExceptionReporter;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.api.internal.project.ServiceRegistry;

import org.gradle.launcher.exec.ReportedException;

import org.gradle.logging.StyledTextOutputFactory;

/**
 * Reports the execution exception if it exists after execution.
 */
public class ReportExceptions implements DaemonCommandAction {
    
    final private ServiceRegistry loggingServices;
    
    public ReportExceptions(ServiceRegistry loggingServices) {
        this.loggingServices = loggingServices;
    }
    
    public void execute(final DaemonCommandExecution execution) {
        execution.proceed();

        Throwable exception = execution.getException();
        if (exception != null && !(exception instanceof ReportedException)) {
            StyledTextOutputFactory outputFactory = loggingServices.get(StyledTextOutputFactory.class);
            BuildClientMetaData clientMetaData = execution.getCommand().getClientMetaData();
            BuildExceptionReporter exceptionReporter = new BuildExceptionReporter(outputFactory, new StartParameter(), clientMetaData);
            exceptionReporter.reportException(exception);

            execution.setException(new ReportedException(exception));
        }
    }
}
