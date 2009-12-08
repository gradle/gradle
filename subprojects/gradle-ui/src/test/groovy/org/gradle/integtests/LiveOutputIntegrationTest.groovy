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
package org.gradle.integtests
 
import org.gradle.gradleplugin.foundation.GradlePluginLord
import org.junit.runner.RunWith
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.Assert;
import org.gradle.util.GFileUtils
import org.gradle.foundation.ipc.gradle.ExecuteGradleCommandServerProtocol
import org.gradle.gradleplugin.foundation.runner.GradleRunner
import org.gradle.foundation.TestUtility;
 
/**
This tests the that live output is gathered while executing a task.
@author mhunsicker
*/
@RunWith(DistributionIntegrationTestRunner.class)
class LiveOutputIntegrationTest {
 
    static final String JAVA_PROJECT_NAME = 'javaproject'
    static final String SHARED_NAME = 'shared'
    static final String API_NAME = 'api'
    static final String WEBAPP_NAME = 'webservice'
    static final String SERVICES_NAME = 'services'
    static final String WEBAPP_PATH = "$SERVICES_NAME/$WEBAPP_NAME" as String
 
    private File javaprojectDir
    private List projects;
 
    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;
 
    @Before
    void setUp() {
        javaprojectDir = new File(dist.samplesDir, 'java/multiproject')
        projects = [SHARED_NAME, API_NAME, WEBAPP_NAME, SERVICES_NAME].collect {"JAVA_PROJECT_NAME/$it"} + JAVA_PROJECT_NAME
        deleteBuildDir(projects)
    }
 
    @After
    void tearDown() {
        deleteBuildDir(projects)
    }
 
    private def deleteBuildDir(List projects) {
        return projects.each {GFileUtils.deleteDirectory(new File(dist.samplesDir, "$it/build"))}
    }
 
    /**
This executes 'build' on the java multiproject sample. We want to make sure that
we do get live output from gradle. We're not concerned with what it is, because
that's likely to change over time. This version executes the command via GradlePlugin.
 
@author mhunsicker
*/
 
    @Test
    public void liveOutputObtainedViaGradlePlugin() {
        // Build and test projects
        executer.inDirectory(javaprojectDir).withTasks('assemble').run();
 
        File multiProjectDirectory = new File(dist.getSamplesDir(), "java/multiproject");
        Assert.assertTrue(multiProjectDirectory.exists()); //make sure things are setup the way we expect
 
        GradlePluginLord gradlePluginLord = new GradlePluginLord();
        gradlePluginLord.setCurrentDirectory(multiProjectDirectory);
        gradlePluginLord.setGradleHomeDirectory(dist.gradleHomeDir);
 
        gradlePluginLord.startExecutionQueue(); //for tests, we'll need to explicitly start the execution queue (unless we do a refresh via the TestUtility).

        TestExecutionInteraction executionInteraction = new TestExecutionInteraction();

        //execute a command. We don't really care what the command is, just something that generates output
        TestUtility.executeBlocking( gradlePluginLord, "-t", "Test Execution", executionInteraction, 45 )

        verifyLiveOutputObtained( executionInteraction );
    }
 
    /**
This executes 'build' on the java multiproject sample. We want to make sure that
we do get live output from gradle. We're not concerned with what it is, because
that's likely to change over time. This version executes the command via GradleRunner.
 
@author mhunsicker
*/
    @Test
    public void liveOutputObtainedViaGradleRunner() {
        // Build and test projects
        executer.inDirectory(javaprojectDir).withTasks('assemble').run();
 
        File multiProjectDirectory = new File(dist.getSamplesDir(), "java/multiproject");
        Assert.assertTrue(multiProjectDirectory.exists()); //make sure things are setup the way we expect
 
        GradleRunner gradleRunner = new GradleRunner( multiProjectDirectory, dist.gradleHomeDir, null );
 
        TestExecutionInteraction executionInteraction = new TestExecutionInteraction();
 
        //execute a command. We don't really care what the command is, just something that generates output
        gradleRunner.executeCommand("-t", org.gradle.api.logging.LogLevel.LIFECYCLE,
                                            org.gradle.StartParameter.ShowStacktrace.INTERNAL_EXCEPTIONS,
                                            executionInteraction);

        //now sleep until we're complete, but bail if we wait too long
        int maximumWaitSeconds = 45; //this is totally arbitrary and is worse case senario.
        int totalWaitTime = 0;
        while( !executionInteraction.executionFinishedReported && totalWaitTime <= maximumWaitSeconds) {
            try {
               println "Waiting. Has Finished: " + executionInteraction.executionFinishedReported + ". Total Time: "+ totalWaitTime
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }

            totalWaitTime += 1;
        }
      
        verifyLiveOutputObtained( executionInteraction );
    }
 
 
 
   /**
  This verifies that it has live output. It also checks that we received some final output as well
  as that the execution was successful
  */
   private void verifyLiveOutputObtained( TestExecutionInteraction executionInteraction )
   {
      //Make sure we were successful. If we weren't successful, that probably indicates a different problem and the test itself may be invalid.
      Assert.assertTrue( "Verifying execution was successful", executionInteraction.wasSuccessful )

      //verify that we actually finished. If not, then we timed out, which may mean the machine is really slow or that there's a serious problem.
      Assert.assertTrue( "Verifying execution finished in a timely manner", executionInteraction.executionFinishedReported );

      //make sure we received some output! I just made up 30 because I wanted more than just 1 character and there should actually be dozens of characters.
      Assert.assertTrue( "Verifying live output was obtained", executionInteraction.liveOutput.length() >= 30 )

      //We should also get a final message. Note: this is usually a little different from the live output, if for not other reason than
      //timing issues of when the last live output is sent. The final message should have everything, but we might not get the last
      //live output. As such, we won't verify they're equal.
      Assert.assertTrue( "Verifying the final output message was received", executionInteraction.finalMessage.length() > 30 )
   }
}
 
//this class just holds onto our liveOutput and also tracks whether or not we've finished.
public class TestExecutionInteraction implements ExecuteGradleCommandServerProtocol.ExecutionInteraction
{
   private StringBuilder liveOutput = new StringBuilder();
   private boolean executionFinishedReported = false;
   private boolean wasSuccessful = false;
   private String finalMessage;
 
   public void reportLiveOutput(String message)
   {
      liveOutput.append( message );
   }
 
   //when we finish executing, we'll make sure we got some type of live output from gradle.
   public void reportExecutionFinished(boolean wasSuccessful, String message, Throwable throwable)
   {
      println "Received execution finished"
      executionFinishedReported = true
      this.wasSuccessful = true
      this.finalMessage = message
   }
 
   public void reportExecutionStarted() { }
   public void reportNumberOfTasksToExecute(int size) { }
   public void reportTaskStarted(String message, float percentComplete) { }
   public void reportTaskComplete(String message, float percentComplete) { }
 
 
 
}