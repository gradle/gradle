/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.GradleException;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import java.io.File;

public class TapiClient {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: TapiClient <projectDir> <taskToRun>");
            System.exit(1);
        }
        simpleTaskExecutionExample(new File(args[0]), args[1]);
    }

    private static void simpleTaskExecutionExample(File sampleProjectDir, String taskToRun) {
        final ProblemProgressListener listener = new ProblemProgressListener();

        try {
            try (ProjectConnection projectConnection = createConnector(sampleProjectDir)) {
                projectConnection
                    .newBuild()
                    .addProgressListener(listener)
                    .forTasks(taskToRun)
                    .run();

            }
        } catch (BuildException ex) {
            System.err.println("Error running task: " + ex.getMessage());
        }

        ProblemRenderer.render(listener.getCollectedProblems(), listener.getSummaryCounts());
    }

    private static ProjectConnection createConnector(File sampleProjectDir) {
        return GradleConnector.newConnector()
            .forProjectDirectory(sampleProjectDir)
            .connect();
    }

}
