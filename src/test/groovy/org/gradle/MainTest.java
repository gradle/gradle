/*
 * Copyright 2007 the original author or authors.
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

package org.gradle;

import org.gradle.util.HelperUtil;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class MainTest {
    private static final String[] TEST_ARGS = { "arg1", "arg2" };
    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    private void setUpGradle(final BuildResult buildResult, final StartParameter startParameter) {
        final Gradle gradleMock = context.mock(Gradle.class);
        final GradleFactory gradleFactoryMock = context.mock(GradleFactory.class);

        Gradle.injectCustomFactory(gradleFactoryMock);
        context.checking(new Expectations() {{
            one(gradleFactoryMock).newInstance(startParameter); will(returnValue(gradleMock));
            one(gradleMock).addBuildListener(with(notNullValue(BuildExceptionReporter.class)));
            one(gradleMock).addBuildListener(with(notNullValue(BuildResultLogger.class)));
            one(gradleMock).run();
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
    }

    @Test
    public void showVersion() throws Exception {
        // This tests just that showVersion does not lead to running a build or throwing of an exception.
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
    }

    @Test
    public void illegalCommandLineArgs() throws Exception {
        final CommandLine2StartParameterConverter commandLine2StartParameterConverterMock =
                context.mock(CommandLine2StartParameterConverter.class, "exceptionMock");
        Main main = new Main(TEST_ARGS);
        final CommandLineArgumentException conversionException = new CommandLineArgumentException();
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
    }


    private class BuildCompletedError extends Error {
        public BuildCompletedError(Throwable failure) {
            super(failure);
        }
    }
}

