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
import org.gradle.foundation.output.FileLink
import org.gradle.foundation.output.FileLinkDefinitionLord
import org.gradle.foundation.output.LiveOutputParser
import org.gradle.gradleplugin.foundation.GradlePluginLord
import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
This tests the that live output is gathered while executing a task.
*/
class LiveOutputIntegrationTest extends AbstractIntegrationTest {

    private File javaprojectDir

    @Rule public final Sample sample = new Sample(testDirectoryProvider, 'java/quickstart')

    @Before
    void setUp() {
        NativeServicesTestFixture.initialize()
        javaprojectDir = sample.dir
    }

    /**
This executes 'build' on the java quickstart sample. We want to make sure that
we do get live output from gradle. We're not concerned with what it is, because
that's likely to change over time. This version executes the command via GradlePlugin.
*/

    @Test
    public void liveOutputObtainedViaGradlePlugin() {
        File multiProjectDirectory = sample.getDir();
        Assert.assertTrue(multiProjectDirectory.exists()); //make sure things are setup the way we expect

        GradlePluginLord gradlePluginLord = new GradlePluginLord();
        gradlePluginLord.setCurrentDirectory(multiProjectDirectory);
        gradlePluginLord.setGradleHomeDirectory(distribution.gradleHomeDir);
        gradlePluginLord.addCommandLineArgumentAlteringListener(new ExtraTestCommandLineOptionsListener(executer.gradleUserHomeDir))

        TestExecutionInteraction executionInteraction = new TestExecutionInteraction();

        //execute a command. We don't really care what the command is, just something that generates output
        TestUtility.executeBlocking( gradlePluginLord, "tasks", "Test Execution", executionInteraction, 100 )

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



     /**
      Tests that navigating to the next file link works correctly. It should circle back around if we
      search passed the end.
      */
     @Test
     public void testNextFileLinks()
     {
       LiveOutputParser parser = new LiveOutputParser( new FileLinkDefinitionLord(), false );  //we don't really need/want real live output for this test

       //should not find a match. There's absolutely no text present.
       Assert.assertNull( parser.getNextFileLink( 0 ) );

       parser.appendText( "Beginning\n");

       //should not find a match. There's no links present.
       Assert.assertNull( parser.getNextFileLink( 0 ) );

       parser.appendText( "first.java:1\n");    //file link
       parser.appendText( "middle 1\n");
       parser.appendText( "second.java:2\n");   //file link
       parser.appendText( "middle 2\n");
       parser.appendText( "next.java:3\n");    //file link
       parser.appendText( "last\n");

       List<FileLink> links = parser.getFileLinks();
       Assert.assertEquals( 3, links.size() );

       Assert.assertEquals( new File( "first.java" ), links.get( 0 ).getFile() );
       Assert.assertEquals( new File( "second.java" ), links.get( 1 ).getFile() );
       Assert.assertEquals( new File( "next.java" ), links.get( 2 ).getFile() );

       //now do a series of searching next.
       FileLink link1 = parser.getNextFileLink( 0 );
       Assert.assertEquals( links.get( 0 ), link1 );

       FileLink link2 = parser.getNextFileLink( link1.getEndingIndex() );
       Assert.assertEquals( links.get( 1 ), link2 );

       FileLink link3 = parser.getNextFileLink( link2.getEndingIndex() );
       Assert.assertEquals( links.get( 2 ), link3 );

       //search once more. This should go back to the beginning
       FileLink link4 = parser.getNextFileLink( link3.getEndingIndex() );
       Assert.assertEquals( link1, link4 );
}

     /**
      Tests that navigating to the previous file link works correctly. It should circle back around if we
      search passed the beginning.
      */
     @Test
     public void testPreviousFileLinks()
     {
       LiveOutputParser parser = new LiveOutputParser( new FileLinkDefinitionLord(), false );  //we don't really need/want real live output for this test

       //should not find a match. There's absolutely no text present.
       Assert.assertNull( parser.getPreviousFileLink( 0 ) );

       parser.appendText( "Beginning\n");

       //should not find a match. There's no links present.
       Assert.assertNull( parser.getPreviousFileLink( 0 ) );

       parser.appendText( "first.java:1\n");    //file link
       parser.appendText( "middle 1\n");
       parser.appendText( "second.java:2\n");   //file link
       parser.appendText( "middle 2\n");
       parser.appendText( "next.java:3\n");    //file link
       parser.appendText( "last\n");

       List<FileLink> links = parser.getFileLinks();
       Assert.assertEquals( 3, links.size() );

       Assert.assertEquals( new File( "first.java" ), links.get( 0 ).getFile() );
       Assert.assertEquals( new File( "second.java" ), links.get( 1 ).getFile() );
       Assert.assertEquals( new File( "next.java" ), links.get( 2 ).getFile() );

       //now do a series of searching previous starting at the end.
       FileLink link3 = parser.getPreviousFileLink( parser.getText().length() - 1 );
       Assert.assertEquals( links.get( 2 ), link3  );

       FileLink link2 = parser.getPreviousFileLink( link3.getStartingIndex() );
       Assert.assertEquals( links.get( 1 ), link2 );

       FileLink link1 = parser.getPreviousFileLink( link2.getStartingIndex() );
       Assert.assertEquals( links.get( 0 ), link1 );

       //search once more. This should go back to the ending
       FileLink link4 = parser.getPreviousFileLink( link1.getStartingIndex() );
       Assert.assertEquals( link3, link4 );
     }

}
