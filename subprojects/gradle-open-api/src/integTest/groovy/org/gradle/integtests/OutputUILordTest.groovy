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
package org.gradle.integtests;

import org.gradle.openapi.external.ui.SinglePaneUIVersion1
import org.gradle.openapi.external.ui.UIFactory
import org.gradle.util.GFileUtils

import junit.framework.AssertionFailedError
import org.junit.runner.RunWith
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.Assert
import org.gradle.openapi.external.ui.OutputUILordVersion1
import java.awt.Font
import javax.swing.UIManager
import org.gradle.openapi.external.foundation.RequestObserverVersion1
import org.gradle.openapi.external.foundation.RequestVersion1
import org.gradle.openapi.external.foundation.GradleInterfaceVersion1
import org.junit.Rule
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter

/**
 * Tests aspects of the OutputUILord in OpenAPI
 *
 * @author mhunsicker
 */
@RunWith(DistributionIntegrationTestRunner.class)
public class OutputUILordTest  {
  static final String JAVA_PROJECT_NAME = 'javaproject'
  static final String SHARED_NAME = 'shared'
  static final String API_NAME = 'api'
  static final String WEBAPP_NAME = 'webservice'
  static final String SERVICES_NAME = 'services'
  static final String WEBAPP_PATH = "$SERVICES_NAME/$WEBAPP_NAME" as String

  private File javaprojectDir
  private List projects;

  @Rule public final GradleDistribution dist = new GradleDistribution()
  @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()

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
      return projects.each { GFileUtils.deleteDirectory(new File(dist.samplesDir, "$it/build"))}
  }

  /**
   * Helper function that creates a single pane UI
   */
   private SinglePaneUIVersion1 createSinglePaneUI()
   {
     TestSingleDualPaneUIInteractionVersion1 testSingleDualPaneUIInteractionVersion1 = new TestSingleDualPaneUIInteractionVersion1( new TestAlternateUIInteractionVersion1(), new TestSettingsNodeVersion1() )
     SinglePaneUIVersion1 singlePane = UIFactory.createSinglePaneUI(getClass().getClassLoader(), dist.getGradleHomeDir(), testSingleDualPaneUIInteractionVersion1, false )

     //make sure we got something
     Assert.assertNotNull( singlePane )

     singlePane.setCurrentDirectory( javaprojectDir )

     return singlePane
   }

  /**
  * This verifies that you can add file extension to the output lord. This is for
  * highlighting file links in the output. Here, we're just interested in whether
  * or not the functions work via/exists in the Open API. The actual functionality
  * is tested elsewhere.
  */
  @Test
  public void testAddingFileExtension()
  {
    SinglePaneUIVersion1 singlePane = createSinglePaneUI()
    OutputUILordVersion1 outputUILord = singlePane.getOutputLord()

    outputUILord.addFileExtension( '.txt', ':' )
    List extensions = outputUILord.getFileExtensions()
    Assert.assertTrue( extensions.contains( '.txt' ) )
  }

  /**
  * This verifies that you can add prefixed file extensions to the output lord. This
  * is for highlighting file links in the output. Here, we're just interested in whether
  * or not the functions work via/exists in the Open API. The actual functionality is tested elsewhere.
  */
  @Test
  public void testAddingPrefixedFileLink()
  {
    SinglePaneUIVersion1 singlePane = createSinglePaneUI()
    OutputUILordVersion1 outputUILord = singlePane.getOutputLord()

    outputUILord.addPrefixedFileLink( "Error Text", "The error is:", ".txt", ":" )
  }

  /**
  * This tests setting the font. There's not much here to do other than set it and then
  * get it, making sure its the same. This isn't worried so much about the font itself as
  * much as the open API doesn't have a problem with setting the font.
  */
  @Test
  public void testFont()
  {
    if ( java.awt.GraphicsEnvironment.isHeadless() ) {
       return;  // Can't run this test in headless mode!
    }

    SinglePaneUIVersion1 singlePane = createSinglePaneUI()
    OutputUILordVersion1 outputUILord = singlePane.getOutputLord()
    Font font = UIManager.getFont( "Button.font" )  //this specific font is not important

    //make sure that the above font doesn't happen to be the default font for the output lord. If it
    //is, this test will silently succeed even if it should fail.
    Assert.assertNotSame( "Fonts are the same. This test is not setup correctly.", font, outputUILord.getOutputTextFont() )

    //now set the new font and then make sure it worked
    outputUILord.setOutputTextFont( font )
  }

  /**
  *
  */
  @Test
  public void testReExecute()
  {
    SinglePaneUIVersion1 singlePane = createSinglePaneUI()
    OutputUILordVersion1 outputUILord = singlePane.getOutputLord()

    //this starts the execution queue. This also initiates a refresh that we'll ignore later.
    singlePane.aboutToShow()

    TestRequestObserver2 testRequestObserver = new TestRequestObserver2()
    singlePane.getGradleInterfaceVersion1().addRequestObserver( testRequestObserver )

    //now execute a command
    singlePane.executeCommand( "build", "test build")

    //wait for it to complete
    waitForCompletion( testRequestObserver, singlePane.getGradleInterfaceVersion1(), 80 )

    //now the single command we're trying to test
    outputUILord.reExecuteLastCommand();

    //wait again for it exit
    waitForCompletion( testRequestObserver, singlePane.getGradleInterfaceVersion1(), 80 )

    //make sure it executed the correct request
    Assert.assertEquals( "Incorrect request", "build", testRequestObserver.executionRequest.getFullCommandLine() )
  }

  private void waitForCompletion( TestRequestObserver2 testRequestObserver, GradleInterfaceVersion1 gradleInterface, int maximumWaitTime )
  {
    int totalWaitTime = 0;
    while ( testRequestObserver.executionRequest == null && totalWaitTime <= maximumWaitTime ) {
        try {
            Thread.sleep(1000);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        totalWaitTime += 1;
    }

    if( totalWaitTime > maximumWaitTime ) {
      throw new AssertionFailedError("Waited " + totalWaitTime + " seconds and failed to finish executing command. This is taking too long, so assuming something is wrong.\nCurrent project directory: '" + gradleInterface.getCurrentDirectory() + "'\ngradle home: '" + gradleInterface.getGradleHomeDirectory() + "'\nOutput:\n" + testRequestObserver.output)
    }
  }
}

    /**
      * This allows us to get a copy of the request that was executed so we can inspect it when its done
      */
    private class TestRequestObserver2 implements RequestObserverVersion1
    {
      public RequestVersion1 executionRequest
      public int result = -98 //means it hasn't been set to anything. 0 means success, so we have to initialize it to something else
      public String output

      void executionRequestAdded(RequestVersion1 request) { }
      void refreshRequestAdded(RequestVersion1 request) { }
      void aboutToExecuteRequest(RequestVersion1 request) { }

      void requestExecutionComplete(RequestVersion1 request, int result, String output) {
        if( RequestVersion1.EXECUTION_TYPE.equals( request.getType() ) ) {  //refreshes will come through here. We're ignoring those
          this.executionRequest = request
          this.result = result
          this.output = output
        }
      }

      public void reset() {
        this.executionRequest = null;
        this.result = -98;
        this.output = null;
      }

    }

