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

import org.gradle.foundation.TestUtility
import org.gradle.foundation.ipc.gradle.ExecuteGradleCommandServerProtocol
import org.gradle.gradleplugin.foundation.GradlePluginLord
import org.gradle.gradleplugin.foundation.runner.GradleRunner
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.Sample
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.Condition

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

    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule public final Sample sample = new Sample('java/quickstart')

    @Before
    void setUp() {
        javaprojectDir = sample.dir
    }

    /**
This executes 'build' on the java multiproject sample. We want to make sure that
we do get live output from gradle. We're not concerned with what it is, because
that's likely to change over time. This version executes the command via GradlePlugin.

@author mhunsicker
*/

    @Test
    public void liveOutputObtainedViaGradlePlugin() {
       System.out.println("project dir: " + javaprojectDir );
        // Build and test projects
        executer.inDirectory(javaprojectDir).withTasks('assemble').run();

        File multiProjectDirectory = sample.getDir();
        Assert.assertTrue(multiProjectDirectory.exists()); //make sure things are setup the way we expect

        GradlePluginLord gradlePluginLord = new GradlePluginLord();
        gradlePluginLord.setCurrentDirectory(multiProjectDirectory);
        gradlePluginLord.setGradleHomeDirectory(dist.gradleHomeDir);

        gradlePluginLord.startExecutionQueue(); //for tests, we'll need to explicitly start the execution queue (unless we do a refresh via the TestUtility).

        TestExecutionInteraction executionInteraction = new TestExecutionInteraction();

        //execute a command. We don't really care what the command is, just something that generates output
        TestUtility.executeBlocking( gradlePluginLord, "tasks", "Test Execution", executionInteraction, 80 )

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

        File multiProjectDirectory = sample.getDir();
        Assert.assertTrue(multiProjectDirectory.exists()); //make sure things are setup the way we expect

        GradleRunner gradleRunner = new GradleRunner( multiProjectDirectory, dist.gradleHomeDir, null );

        TestExecutionInteraction executionInteraction = new TestExecutionInteraction();

        //execute a command. We don't really care what the command is, just something that generates output
        gradleRunner.executeCommand("tasks", org.gradle.api.logging.LogLevel.LIFECYCLE,
                                            org.gradle.StartParameter.ShowStacktrace.INTERNAL_EXCEPTIONS,
                                            executionInteraction);

        executionInteraction.waitForCompletion(80, TimeUnit.SECONDS)

        verifyLiveOutputObtained( executionInteraction );
    }



   /**
  This verifies that it has live output. It also checks that we received some final output as well
  as that the execution was successful
  */
   private void verifyLiveOutputObtained( TestExecutionInteraction executionInteraction )
   {
      executionInteraction.assertCompleted()

      //Make sure we were successful. If we weren't successful, that probably indicates a different problem and the test itself may be invalid.
      Assert.assertTrue( String.format("Verifying execution was successful failed:%n%s", executionInteraction.finalMessage), executionInteraction.wasSuccessful )

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
public class TestExecutionInteraction implements ExecuteGradleCommandServerProtocol.ExecutionInteraction {
    private StringBuilder liveOutput = new StringBuilder();
    public boolean executionFinishedReported = false;
    public boolean wasSuccessful = false;
    public String finalMessage;
    private Throwable failure
    private final Lock lock = new ReentrantLock()
    private final Condition condition = lock.newCondition()

    public void reportLiveOutput(String message) {
        liveOutput.append(message);
    }

    //when we finish executing, we'll make sure we got some type of live output from gradle.

    public void reportExecutionFinished(boolean wasSuccessful, String message, Throwable throwable) {
        lock.lock()
        try {
            executionFinishedReported = true
            this.wasSuccessful = wasSuccessful
            this.finalMessage = message
            failure = throwable
            condition.signalAll()
        } finally {
            lock.unlock()
        }
    }

    def assertCompleted() {
        lock.lock()
        try {
            if (!executionFinishedReported) {
                throw new AssertionError("Request has not completed.")
            }
        } finally {
            lock.unlock()
        }
    }

    public waitForCompletion(int maxWaitValue, TimeUnit maxWaitUnits) {
        Date expiry = new Date(System.currentTimeMillis() + maxWaitUnits.toMillis(maxWaitValue))
        lock.lock()
        try {
            while (!executionFinishedReported) {
                if (!condition.awaitUntil(expiry)) {
                    throw new AssertionError("Timeout waiting for execution to complete.")
                }
            }
            if (failure != null) {
                throw failure
            }
        } finally {
            lock.unlock()
        }
    }

    public void reportExecutionStarted() { }

    public void reportNumberOfTasksToExecute(int size) { }

    public void reportTaskStarted(String message, float percentComplete) { }

    public void reportTaskComplete(String message, float percentComplete) { }


}