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

import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Hans Dockter
 */
public class Main {
    private static Logger logger = LoggerFactory.getLogger(Main.class);

    public static final String GRADLE_HOME_PROPERTY_KEY = "gradle.home";
    public static final String DEFAULT_GRADLE_USER_HOME = System.getProperty("user.home") + "/.gradle";
    public final static String DEFAULT_PLUGIN_PROPERTIES = "plugin.properties";
    public final static String IMPORTS_FILE_NAME = "gradle-imports";
    
    private final String[] args;
    private BuildCompleter buildCompleter = new ProcessExitBuildCompleter();

    public Main(String[] args) {
        this.args = args;
    }

    public static void main(String[] args) throws Throwable {
        new Main(args).execute();
    }

    void setBuildCompleter(BuildCompleter buildCompleter) {
        this.buildCompleter = buildCompleter;
    }

    public void execute() throws Exception {
        StartParameter startParameter = null;

        DefaultCommandLine2StartParameterConverter parameterConverter = new DefaultCommandLine2StartParameterConverter();
        try {
            startParameter = parameterConverter.convert(args);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            parameterConverter.showHelp(System.err);
            buildCompleter.exit(e);
        }

        if (startParameter.isShowHelp()) {
            parameterConverter.showHelp(System.out);
            buildCompleter.exit(null);
        }

        if (startParameter.isShowVersion()) {
            System.out.println(new GradleVersion().prettyPrint());
            buildCompleter.exit(null);
        }

        BuildResultLogger resultLogger = new BuildResultLogger(logger);
        BuildExceptionReporter exceptionReporter = new BuildExceptionReporter(logger);
        try {
            exceptionReporter.setStartParameter(startParameter);
            Gradle gradle = Gradle.newInstance(startParameter);

            gradle.addBuildListener(exceptionReporter);
            gradle.addBuildListener(resultLogger);

            BuildResult buildResult = gradle.run();
            if (buildResult.getFailure() != null) {
                buildCompleter.exit(buildResult.getFailure());
            }
        } catch (Throwable e) {
            exceptionReporter.buildFinished(new BuildResult(null, e));
            buildCompleter.exit(e);
        }
        buildCompleter.exit(null);
    }

    public interface BuildCompleter {
        void exit(Throwable failure);
    }

    private static class ProcessExitBuildCompleter implements BuildCompleter {
        public void exit(Throwable failure) {
            System.exit(failure == null ? 0 : 1);
        }
    }
}
