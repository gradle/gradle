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
package org.gradle.foundation;

import junit.framework.TestCase;
import org.gradle.foundation.output.FileLink;
import org.gradle.foundation.output.FileLinkDefinitionLord;
import org.gradle.foundation.output.LiveOutputParser;

import java.io.File;
import java.util.List;

/**
 * Tests aspects of LiveOutputParser. This finds FileLinks within text that is being output constantly.
 */
public class LiveOutputParserTests extends TestCase {
    private LiveOutputParser parser;
    private FileLinkDefinitionLord definitionLord;

    @Override
    protected void setUp() throws Exception {
        definitionLord = new FileLinkDefinitionLord();
        parser = new LiveOutputParser(definitionLord, false);
    }

    @Override
    protected void tearDown() throws Exception {
        definitionLord = null;
        parser = null; //clean up after ourselves. Some test runners keep all tests in memory. This makes sure our parser isn't consuming any.
    }

    /**
     * This does a basic test. Text is output in several waves breaking within lines. There is a single file link in it. We should find it. Specifically, this is going to break up the file so it comes
     * in multiple parts.
     */
    public void testBasic() {
        FileLink expectedFileLink = new FileLink(new File("/home/user/project/modules/plugins/src/main/java/com/thing/plugins/gradle/ui/GradleComponent.java"), 114, 215, 186);

        appendTextWithoutFileLinks(":distributionDiskResources ");
        appendTextWithoutFileLinks("SKIPPED\n:installDiskResources SKIPPED\n");
        appendTextWithoutFileLinks(":idea-plugins:ideagradle:compileJava\n");
        appendTextWithoutFileLinks("[ant:javac] /home/user/project/modules/plugins");
        appendTextWithoutFileLinks("/src/main/java/com/thing/plugins/gradle/ui/Gradle");
        appendTextWithFileLinks("Component.java:186: cannot find symbol\n", expectedFileLink);  //here's where we expect to get some results
        appendTextWithoutFileLinks("[ant:javac] symbol  : constructor Integer()\n");
        appendTextWithoutFileLinks("[ant:javac] location: class java.lang.Integer\n");
        appendTextWithoutFileLinks("[ant:javac]       SwingUtilities.invokeLater( new Integer() );\n");
        appendTextWithoutFileLinks("[ant:javac]                                   ^\n");

        //at the end, verify we only found what was expected
        TestUtility.assertListContents(parser.getFileLinks(), expectedFileLink);
    }

    private void appendTextWithoutFileLinks(String text) {
        List<FileLink> fileLinks = parser.appendText(text);
        if (!fileLinks.isEmpty()) {
            throw new AssertionError("FileLinks list is erroneously not empty: " + TestUtility.dumpList(fileLinks));
        }
    }

    private void appendTextWithFileLinks(String text, FileLink... expectedResults) {
        List<FileLink> fileLinks = parser.appendText(text);
        TestUtility.assertListContents(fileLinks, expectedResults);
    }

    /**
     * This tests live output coming in where the result is multiple FileLinks. We'll just add many lines some have FileLinks some don't. We want to make sure the LiveOutputParser tracks all of them
     * correctly.
     */
    public void testMultipleFiles() {
        FileLink fileLink1 = new FileLink(new File("/home/user/modules/gradle/subprojects/gradle-core/src/main/groovy/org/gradle/util/exec/ExecHandleShutdownHookAction.java"), 120, 243, 38);
        FileLink fileLink2 = new FileLink(new File("/home/user/modules/gradle/subprojects/gradle-core/src/test/groovy/org/gradle/integtests/DistributionIntegrationTestRunner.java"), 282, 408, -1);
        FileLink fileLink3 = new FileLink(new File("/home/user/modules/gradle/subprojects/gradle-core/build/checkstyle/main.xml"), 531, 606, -1);
        FileLink fileLink4 = new FileLink(new File("/home/user/modules/gradle/subprojects/gradle-ui/ui.gradle"), 622, 679, -1);
        FileLink fileLink5 = new FileLink(new File("/home/user/modules/gradle/subprojects/gradle-ui/build/reports/codenarc/test.html"), 832, 912, -1);

        appendTextWithoutFileLinks(":distributionDiskResources SKIPPED\n:installDiskResources");
        appendTextWithoutFileLinks(" SKIPPED\n:idea-plugins:ideagradle:compileJava\n\n");
        appendTextWithoutFileLinks("[ant:checkstyle] /home/user/modules/gradle/subprojects");
        appendTextWithFileLinks("/gradle-core/src/main/groovy/org/gradle/util/exec/ExecHandleShutdownHookAction.java:38: 'if' construct must use '{}'s.\n", fileLink1);
        appendTextWithFileLinks(
                "Note: /home/user/modules/gradle/subprojects/gradle-core/src/test/groovy/org/gradle/integtests/DistributionIntegrationTestRunner.java uses or overrides a deprecated API.\n",
                fileLink2);
        appendTextWithoutFileLinks("\n");
        appendTextWithoutFileLinks("Cause: Checkstyle check violations were found in main ");
        appendTextWithoutFileLinks("Java source. See the report ");
        appendTextWithFileLinks("at /home/user/modules/gradle/subprojects/gradle-core/build/checkstyle/main.xml.\n", fileLink3);
        appendTextWithoutFileLinks("\n");
        appendTextWithoutFileLinks("\n");
        appendTextWithFileLinks("Build file '/home/user/modules/gradle/subprojects/gradle-ui/ui.gradle'\n", fileLink4);
        appendTextWithoutFileLinks("\n");
        appendTextWithoutFileLinks("* What went wrong:\n");
        appendTextWithoutFileLinks("Execution failed for task ':ui:codenarcTest'.\n");
        appendTextWithoutFileLinks("Cause: CodeNarc check ");
        appendTextWithFileLinks("violations were found in test Groovy source. See the report at /home/user/modules/gradle/subprojects/gradle-ui/build/reports/codenarc/test.html.\n", fileLink5);
        appendTextWithoutFileLinks("\n");
        appendTextWithoutFileLinks("* Try:\n");
        appendTextWithoutFileLinks("Run with -s or -d option to get more details. Run with -S option to get the full (very verbose) stacktrace.");

        //at the end, verify we only found what was expected
        TestUtility.assertListContents(parser.getFileLinks(), fileLink1, fileLink2, fileLink3, fileLink4, fileLink5);
    }

    /**
     * This tests is we can successfully find FileLinks if several of them come in at once in one big block of multi-lined text. We'll add some text one line at a time, then add a single FileLink (on a
     * single line), then add many many lines at once that has 4 FileLinks in it.
     */
    public void testMultiplesAtOnce() {
        FileLink fileLink1 = new FileLink(new File("/home/user/modules/gradle/subprojects/gradle-core/src/main/groovy/org/gradle/util/exec/ExecHandleShutdownHookAction.java"), 120, 243, 38);
        FileLink fileLink2 = new FileLink(new File("/home/user/modules/gradle/subprojects/gradle-core/src/test/groovy/org/gradle/integtests/DistributionIntegrationTestRunner.java"), 282, 408, -1);
        FileLink fileLink3 = new FileLink(new File("/home/user/modules/gradle/subprojects/gradle-core/build/checkstyle/main.xml"), 531, 606, -1);
        FileLink fileLink4 = new FileLink(new File("/home/user/modules/gradle/subprojects/gradle-ui/ui.gradle"), 622, 679, -1);
        FileLink fileLink5 = new FileLink(new File("/home/user/modules/gradle/subprojects/gradle-ui/build/reports/codenarc/test.html"), 832, 912, -1);

        appendTextWithoutFileLinks(":distributionDiskResources SKIPPED\n");
        appendTextWithoutFileLinks(":installDiskResources SKIPPED\n");
        appendTextWithoutFileLinks(":idea-plugins:ideagradle:compileJava\n\n");
        appendTextWithFileLinks(
                "[ant:checkstyle] /home/user/modules/gradle/subprojects/gradle-core/src/main/groovy/org/gradle/util/exec/ExecHandleShutdownHookAction.java:38: 'if' construct must use '{}'s.\n",
                fileLink1);
        appendTextWithoutFileLinks("Note: /home/user/modules/gradle/"); //does NOT end with a newline. This is just to push a potential edge case

        String remaindingOutputText = "subprojects/gradle-core/src/test/groovy/org/gradle/integtests/DistributionIntegrationTestRunner.java uses or overrides a deprecated API.\n"
                + "\n"
                + "Cause: Checkstyle check violations were found in main Java source. See the report at /home/user/modules/gradle/subprojects/gradle-core/build/checkstyle/main.xml.\n"
                + "\n"
                + "\n"
                + "Build file '/home/user/modules/gradle/subprojects/gradle-ui/ui.gradle'\n"
                + "\n"
                + "* What went wrong:\n"
                + "Execution failed for task ':ui:codenarcTest'.\n"
                + "Cause: CodeNarc check violations were found in test Groovy source. See the report at /home/user/modules/gradle/subprojects/gradle-ui/build/reports/codenarc/test.html.\n"
                + "\n"
                + "* Try:\n"
                + "Run with -s or -d option to get more details. Run with -S option to get the full (very verbose) stacktrace.";

        //now add that one large chunk. We should find the last four
        appendTextWithFileLinks(remaindingOutputText, fileLink2, fileLink3, fileLink4, fileLink5);

        //at the end, verify we only found what was expected
        TestUtility.assertListContents(parser.getFileLinks(), fileLink1, fileLink2, fileLink3, fileLink4, fileLink5);
    }

    /**
     * This verifies that we can find a link to a groovy file as well as its line number. This was actually a bug and this test is based off of actual data (tests failed due to a compile error in
     * groovy). I tracked the problem down to the space after the delimiter (".groovy: 24" vs. ".groovy:24" ). After running this test with actual data, I'm going to test it with a line that doesn't
     * have a space just to make sure both work.
     */
    public void testGroovyFileLineDelimiter() {
        FileLink fileLink1 = new FileLink(new File("/home/user/gradle/subprojects/gradle-open-api/src/integTest/groovy/org/gradle/integtests/GradleRunnerFactoryTest.groovy"), 183, 306, 24);
        FileLink fileLink2 = new FileLink(new File("/home/user/gradle/subprojects/gradle-open-api/src/integTest/groovy/org/gradle/integtests/GradleRunnerFactoryTest.groovy"), 481, 603, 85);

        appendTextWithoutFileLinks(":distributionDiskResources SKIPPED\n");
        appendTextWithoutFileLinks(":installDiskResources SKIPPED\n");
        appendTextWithoutFileLinks(":idea-plugins:ideagradle:compileJava\n\n");
        appendTextWithoutFileLinks("org.codehaus.groovy.control.MultipleCompilationErrorsException: startup failed:\n");

        //notice the space between "GradleRunnerFactoryTest.groovy:" and "24:". That was causing our problem
        appendTextWithFileLinks(
                "/home/user/gradle/subprojects/gradle-open-api/src/integTest/groovy/org/gradle/integtests/GradleRunnerFactoryTest.groovy: 24: unable to find class 'DistributionIntegrationTestRunner.class' for annotation attribute constant\n",
                fileLink1);
        appendTextWithoutFileLinks(" @ line 24, column 10.\n");
        appendTextWithoutFileLinks("   @RunWith(DistributionIntegrationTestRunner.class)\n");

        //now test it without a space between the delimiter and line number to make sure it works both ways
        appendTextWithFileLinks(
                "/home/user/gradle/subprojects/gradle-open-api/src/integTest/groovy/org/gradle/integtests/GradleRunnerFactoryTest.groovy:85: unable to find class 'DistributionIntegrationTestRunner.class' for annotation attribute constant\n",
                fileLink2);
    }

    /**
     * This tests that you can dynamically add file extensions to the parser. We're going to add 2 fake extensions (one with line number delimiter, one without) then verify that the parser correctly
     * parses the output with said extensions.
     */
    public void testAddingFileExtensions() {
        String myExtension1 = ".mytxtextension";
        String myExtension2 = ".othertxtextension";

        //make sure this fake extension isn't already in use
        assertFalse("Fake extension 1 already present. This test is not setup correctly!", definitionLord.getFileExtensions().contains(myExtension1));
        assertFalse("Fake extension 2 already present. This test is not setup correctly!", definitionLord.getFileExtensions().contains(myExtension2));

        definitionLord.addFileExtension(myExtension1, ":");
        definitionLord.addFileExtension(myExtension2, null); //this one has no line delimiter

        //make sure it was added
        assertTrue("Fake extension 1 was not added. ", definitionLord.getFileExtensions().contains(myExtension1));
        assertTrue("Fake extension 2 was not added. ", definitionLord.getFileExtensions().contains(myExtension2));

        //now verify the extension is used

        FileLink fileLink1 = new FileLink(new File("/home/user/gradle/subprojects/gradle-open-api/src/integTest/groovy/org/gradle/integtests/GradleRunnerFactoryTest.mytxtextension"), 183, 313, 24);
        FileLink fileLink2 = new FileLink(new File("/home/user/gradle/subprojects/gradle-open-api/src/integTest/groovy/org/gradle/integtests/GradleRunnerFactoryTest.othertxtextension"), 488, 618, -1);
        FileLink fileLink3 = new FileLink(new File("/home/user/modules/gradle/subprojects/gradle-core/src/main/groovy/org/gradle/util/exec/ExecHandleShutdownHookAction.java"), 632, 755, 38);

        appendTextWithoutFileLinks(":distributionDiskResources SKIPPED\n");
        appendTextWithoutFileLinks(":installDiskResources SKIPPED\n");
        appendTextWithoutFileLinks(":idea-plugins:ideagradle:compileJava\n\n");
        appendTextWithoutFileLinks("org.codehaus.groovy.control.MultipleCompilationErrorsException: startup failed:\n");
        appendTextWithFileLinks(
                "/home/user/gradle/subprojects/gradle-open-api/src/integTest/groovy/org/gradle/integtests/GradleRunnerFactoryTest.mytxtextension:24: unable to find class 'DistributionIntegrationTestRunner.class' for annotation attribute constant\n",
                fileLink1);
        appendTextWithoutFileLinks(" @ line 24, column 10.\n");
        appendTextWithoutFileLinks("   @RunWith(DistributionIntegrationTestRunner.class)\n");
        appendTextWithFileLinks("/home/user/gradle/subprojects/gradle-open-api/src/integTest/groovy/org/gradle/integtests/GradleRunnerFactoryTest.othertxtextension: other error\n", fileLink2);

        //do this just to make sure adding our custom extension didn't break existing extensions
        appendTextWithFileLinks("/home/user/modules/gradle/subprojects/gradle-core/src/main/groovy/org/gradle/util/exec/ExecHandleShutdownHookAction.java:38: 'if' construct must use '{}'s.\n",
                fileLink3);
    }

    /**
     * This tests that you can dynamically add file prefixes to the parser. We're going to add 2 fake prefixes (one with line number delimiter, one without) then verify that the parser correctly parses
     * the output with said prefixes.
     */
    public void testAddingPrefixedFileLink() {
        definitionLord.addPrefixedFileLink("Test Crap 1", "Some Garbage:", ".txt", ":");
        definitionLord.addPrefixedFileLink("Test Crap 2", "Some Trash:", ".txt", null);    //no line delimiter on this one

        FileLink fileLink1 = new FileLink(new File("/home/user/gradle/subprojects/gradle-open-api/src/integTest/groovy/org/gradle/integtests/GradleRunnerFactoryTest.txt"), 206, 325, 24);
        FileLink fileLink2 = new FileLink(new File("/home/user/gradle/subprojects/gradle-open-api/src/integTest/groovy/org/gradle/integtests/GradleRunnerFactoryTest.txt"), 517, 633, -1);
        FileLink fileLink3 = new FileLink(new File("/home/user/modules/gradle/subprojects/gradle-core/src/main/groovy/org/gradle/util/exec/ExecHandleShutdownHookAction.java"), 651, 774, 38);

        appendTextWithoutFileLinks(":distributionDiskResources SKIPPED\n");
        appendTextWithoutFileLinks(":installDiskResources SKIPPED\n");
        appendTextWithoutFileLinks(":idea-plugins:ideagradle:compileJava\n\n");
        appendTextWithoutFileLinks("org.codehaus.groovy.control.MultipleCompilationErrorsException: startup failed:\n");
        appendTextWithFileLinks(
                "Blah blah Some Garbage:/home/user/gradle/subprojects/gradle-open-api/src/integTest/groovy/org/gradle/integtests/GradleRunnerFactoryTest.txt:24: unable to find class 'DistributionIntegrationTestRunner.class' for annotation attribute constant\n",
                fileLink1);
        appendTextWithoutFileLinks(" @ line 24, column 10.\n");
        appendTextWithoutFileLinks("   @RunWith(DistributionIntegrationTestRunner.class)\n");
        appendTextWithFileLinks("Blah Some Trash: /home/user/gradle/subprojects/gradle-open-api/src/integTest/groovy/org/gradle/integtests/GradleRunnerFactoryTest.txt Some other error\n", fileLink2);

        //do this just to make sure adding our prefixed links didn't break existing extensions
        appendTextWithFileLinks("/home/user/modules/gradle/subprojects/gradle-core/src/main/groovy/org/gradle/util/exec/ExecHandleShutdownHookAction.java:38: 'if' construct must use '{}'s.\n",
                fileLink3);
    }
}
