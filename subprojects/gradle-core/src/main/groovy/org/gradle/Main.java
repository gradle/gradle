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
import org.gradle.util.Clock;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.Logger;
import java.lang.reflect.Method;

/**
 * @author Hans Dockter
 */
public class Main {
    private static Logger logger = Logging.getLogger(Main.class);

    public static final String GRADLE_HOME_PROPERTY_KEY = "gradle.home";
    public static final String DEFAULT_GRADLE_USER_HOME = System.getProperty("user.home") + "/.gradle";
    public final static String DEFAULT_PLUGIN_PROPERTIES = "plugin.properties";
    public final static String IMPORTS_FILE_NAME = "gradle-imports";
    
    private final String[] args;
    private BuildCompleter buildCompleter = new ProcessExitBuildCompleter();
    private CommandLine2StartParameterConverter parameterConverter = new DefaultCommandLine2StartParameterConverter();

    public Main(String[] args) {
        this.args = args;
    }

    public static void main(String[] args) throws Throwable {
        new Main(args).execute();
    }

    void setBuildCompleter(BuildCompleter buildCompleter) {
        this.buildCompleter = buildCompleter;
    }

    public void setParameterConverter(CommandLine2StartParameterConverter parameterConverter) {
        this.parameterConverter = parameterConverter;
    }

    public void execute() throws Exception {
        Clock buildTimeClock = new Clock();

        StartParameter startParameter = null;

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


        if (startParameter.isLaunchGUI()){
           try
           {   //due to a circular dependency, we'll have to launch this using reflection.
              Class blockingApplicationClass = getClass().forName( "org.gradle.gradleplugin.userinterface.swing.standalone.BlockingApplication");
              Method method = blockingApplicationClass.getDeclaredMethod( "launchAndBlock" );
              method.invoke( null );
           }
           catch( Throwable e )
           {
              logger.error("Failed to run the UI.", e);
           }

            buildCompleter.exit(null);
        }

        BuildListener resultLogger = new BuildLogger(logger, buildTimeClock, startParameter);
        try {
            GradleLauncher gradleLauncher = GradleLauncher.newInstance(startParameter);

            gradleLauncher.useLogger(resultLogger);

            BuildResult buildResult = gradleLauncher.run();
            if (buildResult.getFailure() != null) {
                buildCompleter.exit(buildResult.getFailure());
            }
        } catch (Throwable e) {
            resultLogger.buildFinished(new BuildResult(null, e));
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
