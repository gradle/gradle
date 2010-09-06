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

package org.gradle.launcher;

import org.gradle.*;
import org.gradle.initialization.CommandLine2StartParameterConverter;
import org.gradle.util.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Hans Dockter
 */
public class MainTest {
    private static final String[] TEST_ARGS = { "arg1", "arg2" };
    private JUnit4Mockery context = new JUnit4GroovyMockery();
    @Rule
    public final SetSystemProperties sysProperties = new SetSystemProperties();
    @Rule
    public final RedirectStdOutAndErr outputs = new RedirectStdOutAndErr();

    private void setUpGradle(final BuildResult buildResult, final StartParameter startParameter) {
        final GradleLauncher gradleMockLauncher = context.mock(GradleLauncher.class);
        final GradleLauncherFactory gradleLauncherFactoryMock = context.mock(GradleLauncherFactory.class);

        GradleLauncher.injectCustomFactory(gradleLauncherFactoryMock);
        context.checking(new Expectations() {{
            one(gradleLauncherFactoryMock).newInstance(startParameter);
            will(returnValue(gradleMockLauncher));
            one(gradleMockLauncher).useLogger(with(any(BuildLogger.class)));
            one(gradleMockLauncher).run();
            will(returnValue(buildResult));
        }});
    }

    private void setUpMain(Main main, final StartParameter startParameter) {
        final CommandLine2StartParameterConverter commandLine2StartParameterConverterStub =
                context.mock(CommandLine2StartParameterConverter.class);
        main.setParameterConverter(commandLine2StartParameterConverterStub);
        main.setBuildCompleter(new Main.BuildCompleter() {
            public void exit(Throwable failure) {
                throw new BuildCompletedError(failure);
            }
        });
        context.checking(new Expectations() {{
            allowing(commandLine2StartParameterConverterStub).convert(TEST_ARGS);
            will(returnValue(startParameter));    
        }});
    }

    @Test
    public void runBuild() throws Exception {
        Main main = new Main(TEST_ARGS);
        BuildResult buildResult = HelperUtil.createBuildResult(null);
        StartParameter startParameter = new StartParameter();
        setUpMain(main, startParameter);
        setUpGradle(buildResult, startParameter);
        try {
            main.execute();
            fail();
        } catch (BuildCompletedError e) {
            assertThat(e.getCause(), nullValue());
        }
    }

    @Test
    public void runBuildWithFailure() throws Exception {
        Main main = new Main(TEST_ARGS);
        RuntimeException exception = new RuntimeException();
        BuildResult buildResult = HelperUtil.createBuildResult(exception);
        StartParameter startParameter = new StartParameter();
        setUpMain(main, startParameter);
        setUpGradle(buildResult, startParameter);
        try {
            main.execute();
            fail();
        } catch (BuildCompletedError e) {
            assertThat((RuntimeException) (e.getCause().getCause()), sameInstance(exception));
        }
    }

    @Test
    public void showHelp() throws Exception {
        final CommandLine2StartParameterConverter commandLine2StartParameterConverterMock =
                context.mock(CommandLine2StartParameterConverter.class, "helpMock");
        Main main = new Main(TEST_ARGS);
        final StartParameter startParameter = new StartParameter();
        startParameter.setShowHelp(true);
        setUpMain(main, startParameter);
        main.setParameterConverter(commandLine2StartParameterConverterMock);
        context.checking(new Expectations() {{
            one(commandLine2StartParameterConverterMock).convert(TEST_ARGS); will(returnValue(startParameter));
            one(commandLine2StartParameterConverterMock).showHelp(System.out);
        }});
        try {
            main.execute();
            fail();
        } catch (BuildCompletedError e) {
            assertThat(e.getCause(), nullValue());
        }

        assertThat(outputs.getStdOut(), containsString("USAGE: gradle [option...] [task...]"));
    }

    @Test
    public void usesSystemPropertyForGradleAppName() {
        System.setProperty("org.gradle.appname", "gradle-app");
        final CommandLine2StartParameterConverter commandLine2StartParameterConverterMock =
                context.mock(CommandLine2StartParameterConverter.class, "helpMock");
        Main main = new Main(TEST_ARGS);
        final StartParameter startParameter = new StartParameter();
        startParameter.setShowHelp(true);
        setUpMain(main, startParameter);
        main.setParameterConverter(commandLine2StartParameterConverterMock);
        context.checking(new Expectations() {{
            one(commandLine2StartParameterConverterMock).convert(TEST_ARGS);
            will(returnValue(startParameter));
            one(commandLine2StartParameterConverterMock).showHelp(System.out);
        }});
        try {
            main.execute();
            fail();
        } catch (BuildCompletedError e) {
            assertThat(e.getCause(), nullValue());
        }

        assertThat(outputs.getStdOut(), containsString("USAGE: gradle-app [option...] [task...]"));
    }

    @Test
    public void showVersion() throws Exception {
        Main main = new Main(TEST_ARGS);
        StartParameter startParameter = new StartParameter();
        startParameter.setShowVersion(true);
        setUpMain(main, startParameter);
        try {
            main.execute();
            fail();
        } catch (BuildCompletedError e) {
            assertThat(e.getCause(), nullValue());
        }

        assertThat(outputs.getStdOut(), containsString(new GradleVersion().prettyPrint()));
    }

    @Test
    public void illegalCommandLineArgs() throws Exception {
        final CommandLine2StartParameterConverter commandLine2StartParameterConverterMock =
                context.mock(CommandLine2StartParameterConverter.class, "exceptionMock");
        Main main = new Main(TEST_ARGS);
        final CommandLineArgumentException conversionException = new CommandLineArgumentException("fail");
        final StartParameter startParameter = new StartParameter();
        setUpMain(main, startParameter);
        main.setParameterConverter(commandLine2StartParameterConverterMock);
        context.checking(new Expectations() {{
            allowing(commandLine2StartParameterConverterMock).convert(TEST_ARGS); will(throwException(conversionException));
            one(commandLine2StartParameterConverterMock).showHelp(System.err);
        }});
        try {
            main.execute();
            fail();
        } catch (BuildCompletedError e) {
            assertThat((CommandLineArgumentException) e.getCause(), sameInstance(conversionException));
        }

        assertThat(outputs.getStdErr(), containsString("USAGE: gradle [option...] [task...]"));
    }


    private class BuildCompletedError extends Error {
        public BuildCompletedError(Throwable failure) {
            super(failure);
        }
    }
}

