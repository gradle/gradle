/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.integtests.openapi

import org.apache.commons.lang.builder.ReflectionToStringBuilder
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.openapi.external.runner.GradleRunnerFactory
import org.gradle.openapi.external.runner.GradleRunnerInteractionVersion1
import org.gradle.openapi.external.runner.GradleRunnerVersion1
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GradleRunnerTest {

  static final String JAVA_PROJECT_NAME = 'javaproject'
  static final String SHARED_NAME = 'shared'
  static final String API_NAME = 'api'
  static final String WEBAPP_NAME = 'webservice'
  static final String SERVICES_NAME = 'services'
  static final String WEBAPP_PATH = "$SERVICES_NAME/$WEBAPP_NAME" as String

  private File javaprojectDir

  @Rule public final GradleDistribution dist = new GradleDistribution()
  @Rule public final TestResources resources = new TestResources('testproject')

  @Before
  void setUp() {
      javaprojectDir = dist.testWorkDir
  }

  /**
   * We just want to make sure we can instantiate a GradleRunner here. That's all
  */
  @Test
  public void testInstantiation()
  {
    TestGradleRunnerInteractionVersion1 interaction = new TestGradleRunnerInteractionVersion1(javaprojectDir)

    GradleRunnerVersion1 runner = GradleRunnerFactory.createGradleRunner(getClass().getClassLoader(), dist.getGradleHomeDir(), interaction, true)

    Assert.assertNotNull( "Failed to instantiate runner", runner )
  }

  /**
   * This does a basic execution. It also checks to make sure that the notifications were fired
   * correctly.
  */
  @Test
  public void testExecution()
  {
    TestGradleRunnerInteractionVersion1 interaction = new TestGradleRunnerInteractionVersion1( javaprojectDir )

    GradleRunnerVersion1 runner = GradleRunnerFactory.createGradleRunner(getClass().getClassLoader(), dist.getGradleHomeDir(), interaction, true)

    Assert.assertNotNull( "Failed to instantiate runner", runner )

    runner.executeCommand( "clean build" )

        //wait for it to complete
    int totalWaitTime = 0;
    int maximumWaitTime = 80
    while ( !interaction.executionFinished && totalWaitTime <= maximumWaitTime ) {
        try {
            Thread.sleep(1000);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        totalWaitTime += 1;
    }

    if( totalWaitTime > maximumWaitTime ) {
      throw new AssertionError( "Waited $totalWaitTime seconds and failed to finish executing command. This is taking too long, so assuming something is wrong.\n. Interaction: $interaction" )
    }

    //now make sure we were notified of things correctly:

    //it should have fired a message that execution has started
    Assert.assertTrue( "Execution did not report started. Interaction: $interaction", interaction.executionStarted )
    
    //it should have finished
    Assert.assertTrue( "Execution did not report finished. Interaction: $interaction", interaction.executionFinished )

    //it should have been successful
    Assert.assertTrue( "Did not execute command successfully. Interaction: $interaction", interaction.wasSuccessful )

    //we should have output
    Assert.assertTrue( "Missing output. Interaction: $interaction", interaction.output.length() > 0 )

    //we should have a message when we finished (basically the full output)
    Assert.assertTrue( "Missing finish message. Interaction: $interaction", interaction.finishMessage != null )

    //there should have been multiple tasks to execute
    Assert.assertTrue( "Not enough tasks executed. Expected multiple. Found $interaction.numberOfTasksToExecute. Interaction: $interaction", interaction.numberOfTasksToExecute > 1 )

    //we should have been notified that tasks started and completed (we're not interested in tracking how many times or specific tasks as that might change too often with releases of gradle.
    Assert.assertTrue( "No tasks reported started. Interaction: $interaction", interaction.taskStarted )
    Assert.assertTrue( "No tasks reported completed. Interaction: $interaction", interaction.taskCompleted )
  }

  /**
   * This tests killing a task. We'll start a build task then kill it after it starts executing.
   * Note: the kill interaction actually kills execution. It waits for a certain number of tasks
   * to be executed.
  */
  @Test
  public void testKill()
  {
    KillTestInteraction interaction = new KillTestInteraction(javaprojectDir)

    GradleRunnerVersion1 runner = GradleRunnerFactory.createGradleRunner(getClass().getClassLoader(), dist.getGradleHomeDir(), interaction, true)

    interaction.runner = runner

    Assert.assertNotNull( "Failed to instantiate runner", runner )

    runner.executeCommand( "build" )

        //wait for it to complete
    int totalWaitTime = 0;
    int maximumWaitTime = 80
    while ( !interaction.executionFinished && totalWaitTime <= maximumWaitTime ) {
        try {
            Thread.sleep(1000);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        totalWaitTime += 1;
    }

    if( totalWaitTime > maximumWaitTime ) {
      throw new AssertionError( "Waited $totalWaitTime seconds and failed to finish executing command. This is taking too long, so assuming something is wrong.\nInteraction: $interaction")
    }

    //make sure we tried to kill the task
    Assert.assertTrue( "Did not attempt to kill execution. Interaction: $interaction", interaction.killedTask )

    //now make sure we were notified of things correctly:

    //it should NOT have been successful
    Assert.assertFalse( "Erroneously executed successfully (was not killed). Interaction: $interaction", interaction.wasSuccessful )

    //it should have fired a message that execution has started
    Assert.assertTrue( "Execution did not report started. Interaction: $interaction", interaction.executionStarted )

    //it should have finished
    Assert.assertTrue( "Execution did not report finished. Interaction: $interaction", interaction.executionFinished )
  }
}

  //Inner class used to track what has been called
  public class TestGradleRunnerInteractionVersion1 implements GradleRunnerInteractionVersion1
  {
    private File workingDirectory
    private StringBuilder output = new StringBuilder()
    private String finishMessage
    boolean wasSuccessful
    boolean executionStarted
    int numberOfTasksToExecute
    boolean executionFinished
    boolean taskCompleted
    boolean taskStarted


    public TestGradleRunnerInteractionVersion1(File workingDirectory) {
      this.workingDirectory = workingDirectory;
    }

    def TestGradleRunnerInteractionVersion1() {
    }

    File getWorkingDirectory() { return workingDirectory }

    GradleRunnerInteractionVersion1.LogLevel getLogLevel() { return GradleRunnerInteractionVersion1.LogLevel.Lifecycle }

    GradleRunnerInteractionVersion1.StackTraceLevel getStackTraceLevel() { return GradleRunnerInteractionVersion1.StackTraceLevel.AlwaysFull }

    void reportExecutionStarted() { executionStarted = true }
    void reportNumberOfTasksToExecute(int size) { numberOfTasksToExecute = size }

    //both of these will be fired often. We're not going to try to track that.
    void reportTaskStarted(String currentTaskName, float percentComplete) { taskStarted = true; }
    void reportTaskComplete(String currentTaskName, float percentComplete) { taskCompleted = true }

    void reportLiveOutput(String output) { this.output.append( output ) }

    void reportExecutionFinished(boolean wasSuccessful, String message, Throwable throwable) {
      this.executionFinished = true;
      this.wasSuccessful = wasSuccessful
      this.finishMessage = message;
    }

    File getCustomGradleExecutable() { return null; }

    String toString() {
      return ReflectionToStringBuilder.toString(this);
    }
  }


  //class to track that has class was started and then kills it. 
  private class KillTestInteraction implements GradleRunnerInteractionVersion1
  {
    private GradleRunnerVersion1 runner
    int tasks = 0
    boolean killedTask

    //after at least 2 tasks start, try to kill the process. This simulates someone killing it while
    //its in the middle of running
    def void reportTaskStarted(String currentTaskName, float percentComplete) {
      tasks++
      if( tasks == 2 ) {
        killedTask = true
        runner.killProcess();
      }
    }
    private File workingDirectory
    boolean wasSuccessful
    boolean executionStarted
    boolean executionFinished

    public KillTestInteraction(File workingDirectory) {
      this.workingDirectory = workingDirectory;
    }

    File getWorkingDirectory() { return workingDirectory }

    GradleRunnerInteractionVersion1.LogLevel getLogLevel() { return GradleRunnerInteractionVersion1.LogLevel.Lifecycle }

    GradleRunnerInteractionVersion1.StackTraceLevel getStackTraceLevel() { return GradleRunnerInteractionVersion1.StackTraceLevel.InternalExceptions }

    void reportExecutionStarted() { executionStarted = true }
    void reportNumberOfTasksToExecute(int size) { }

    void reportTaskComplete(String currentTaskName, float percentComplete) {}
    void reportLiveOutput(String output) {}
    File getCustomGradleExecutable() { return null; }

    void reportExecutionFinished(boolean wasSuccessful, String message, Throwable throwable) {
      this.executionFinished = true;
      this.wasSuccessful = wasSuccessful
    }

    String toString() {
      return ReflectionToStringBuilder.toString(this);
    }
  }