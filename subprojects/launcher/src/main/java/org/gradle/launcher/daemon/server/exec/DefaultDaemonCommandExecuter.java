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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.util.UncheckedException;

import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.logging.internal.LoggingOutputInternal;
import org.gradle.logging.internal.OutputEvent;
import org.gradle.logging.internal.OutputEventListener;

import org.gradle.BuildExceptionReporter;
import org.gradle.StartParameter;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.initialization.DefaultGradleLauncherFactory;

import org.gradle.launcher.exec.DefaultGradleLauncherActionExecuter;
import org.gradle.launcher.exec.ReportedException;
import org.gradle.launcher.env.LenientEnvHacker;

import org.gradle.launcher.daemon.protocol.Build;
import org.gradle.launcher.daemon.protocol.Command;
import org.gradle.launcher.daemon.protocol.CommandComplete;
import org.gradle.launcher.daemon.protocol.Sleep;
import org.gradle.launcher.daemon.protocol.Result;

import org.gradle.messaging.remote.internal.Connection;

import java.util.Map;
import java.util.Properties;

/**
 * The default implementation of how to execute commands that the daemon receives.
 */
public class DefaultDaemonCommandExecuter implements DaemonCommandExecuter {

    private static final Logger LOGGER = Logging.getLogger(DefaultDaemonCommandExecuter.class);

    final private ServiceRegistry loggingServices;
    final private LoggingOutputInternal loggingOutput;
    final private GradleLauncherFactory launcherFactory;

    public DefaultDaemonCommandExecuter(ServiceRegistry loggingServices) {
        this.loggingServices = loggingServices;
        this.loggingOutput = loggingServices.get(LoggingOutputInternal.class);
        this.launcherFactory = new DefaultGradleLauncherFactory(loggingServices);
    }

    public void executeCommand(Connection<Object> connection, Command command) {
        new DaemonCommandExecution(
            connection,
            command,
            new ReturnResult(),
            new ForwardOutput(),
            new ReportExceptions(),
            new HandleSleep(),
            new EstablishBuildEnvironment(),
            new ExecuteBuild()
        ).proceed();
    }

    private class ReturnResult implements DaemonCommandAction {
        public void execute(DaemonCommandExecution execution) {
            execution.proceed();

            Object toReturn = null;
            Throwable exception = execution.getException();
            if (exception != null) {
                if (!(exception instanceof ReportedException)) {
                    LOGGER.error("Could not execute build.", exception);
                }
                toReturn = new CommandComplete(UncheckedException.asUncheckedException(exception));
            } else {
                toReturn = new Result(execution.getResult());
            }

            execution.getConnection().dispatch(toReturn);
        }
    }

    private class ForwardOutput implements DaemonCommandAction {
        public void execute(final DaemonCommandExecution execution) {
            OutputEventListener listener = new OutputEventListener() {
                public void onOutput(OutputEvent event) {
                    try {
                        execution.getConnection().dispatch(event);
                    } catch (Exception e) {
                        //Ignore. It means the client has disconnected so no point sending him any log output.
                        //we should be checking if client still listens elsewhere anyway.
                    }
                }
            };

            loggingOutput.addOutputEventListener(listener);
            execution.proceed();
            loggingOutput.removeOutputEventListener(listener);
        }
    }

    private class ReportExceptions implements DaemonCommandAction {
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

    private class HandleSleep implements DaemonCommandAction {
        public void execute(DaemonCommandExecution execution) {
            Command command = execution.getCommand();
            if (command instanceof Sleep) {
                ((Sleep) command).run();
                execution.setResult(new Result("Command executed successfully: " + command));
                // don't proceed, don't need to go further
            } else {
                execution.proceed();
            }
        }
    }

    private class EstablishBuildEnvironment implements DaemonCommandAction {
        public void execute(DaemonCommandExecution execution) {
            assertIsBuildCommand(execution, this);

            Build build = (Build)execution.getCommand();
            Properties originalSystemProperties = new Properties();
            originalSystemProperties.putAll(System.getProperties());
            Properties clientSystemProperties = new Properties();
            clientSystemProperties.putAll(build.getParameters().getSystemProperties());
            System.setProperties(clientSystemProperties);

            LenientEnvHacker envHacker = new LenientEnvHacker();
            Map<String, String> originalEnv = System.getenv();
            envHacker.setenv(build.getParameters().getEnvVariables());

            //TODO SF I want explicit coverage for this feature
            envHacker.setProcessDir(build.getParameters().getCurrentDir().getAbsolutePath());

            execution.proceed();

            System.setProperties(originalSystemProperties);
            //TODO SF I'm not sure we should set the original env / work dir
            // in theory if character encoding the native code emits doesn't match Java's modified UTF-16
            // we're going to set some rubbish because we used native way to read the env
            envHacker.setenv(originalEnv);
        }
    }

    private class ExecuteBuild implements DaemonCommandAction {
        public void execute(DaemonCommandExecution execution) {
            assertIsBuildCommand(execution, this);
            Build build = (Build)execution.getCommand();
            DefaultGradleLauncherActionExecuter executer = new DefaultGradleLauncherActionExecuter(launcherFactory, loggingServices);
            try {
                Object result = executer.execute(build.getAction(), build.getParameters());
                System.out.println("executed build: " + result);
                execution.setResult(result);
            } catch (Throwable e) {
                execution.setException(e);
            }

            execution.proceed(); // ExecuteBuild should be the last action, but in case we want to decorate the result in the future
        }
    }

    private void assertIsBuildCommand(DaemonCommandExecution execution, DaemonCommandAction action) {
        Command command = execution.getCommand();
        if (!(command instanceof Build)) {
            throw new IllegalStateException(String.format("{} command action received a command that isn't Build (command is {}), this shouldn't happen", action.getClass(), command.getClass()));
        }
    }
}