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
import org.gradle.foundation.output.OutputParser;

import java.io.File;
import java.util.List;

/**
 These test several aspects of parsing output looking for files.
 */
public class FileLinkTests extends TestCase {
    public static void parseOutputTest(String textToSearch, FileLink... expectedResults) {
        parseTest(textToSearch, false, expectedResults);
    }

    public static void parseTest(String textToSearch, boolean verifyFileExists, FileLink... expectedResults) {
        OutputParser outputParser = new OutputParser(new FileLinkDefinitionLord(), verifyFileExists);
        List<FileLink> fileLinks = outputParser.parseText(textToSearch);

        TestUtility.assertListContents(fileLinks, expectedResults);
    }

    public void testCompileErrors() {
       String outputText = ":distributionDiskResources SKIPPED\n"
               + ":installDiskResources SKIPPED\n"
               + ":idea-plugins:ideagradle:compileJava\n"
               + "[ant:javac] /home/user/project/modules/plugins/src/main/java/com/thing/plugins/gradle/ui/GradleComponent.java:186: cannot find symbol\n"
               + "[ant:javac] symbol  : constructor Integer()\n"
               + "[ant:javac] location: class java.lang.Integer\n"
               + "[ant:javac]       SwingUtilities.invokeLater( new Integer() );\n"
               + "[ant:javac]                                   ^\n";

       parseOutputTest(outputText, new FileLink(new File("/home/user/project/modules/plugins/src/main/java/com/thing/plugins/gradle/ui/GradleComponent.java"), 114, 215, 186));
   }

   public void testNotes() {
       String outputText = ":distributionDiskResources SKIPPED\n"
               + ":installDiskResources SKIPPED\n"
               + ":idea-plugins:ideagradle:compileJava\n"
               + "[ant:javac] Note: /home/user/project/modules/plugins/gradle/src/main/java/com/thing/plugins/gradle/ui/GradleComponent.java uses or overrides a deprecated API.\n"
               + "[ant:javac] Note: Recompile with -Xlint:deprecation for details.\n"
               + "[ant:javac] 1 error\n"
               + "Total time: 4.622 secs";

       parseOutputTest(outputText, new FileLink(new File("/home/user/project/modules/plugins/gradle/src/main/java/com/thing/plugins/gradle/ui/GradleComponent.java"), 120, 224, -1));
   }

   /**
    Tests to see if we can find gradle files in the output. This message shows up when the build fails. We'll test two types (that probably wouldn't
    occur naturally together. One with the line number, one without.
    */
   public void testGradleBuildFile() {
       String outputText = "FAILURE: Build failed with an exception.\n"
               + "\n"
               + "* Where:\n"
               + "Build file '/home/user/gradle/build.gradle' line: 431\n"
               + "\n"
               + "* What went wrong:\n"
               + "blah blah blah\n"
               + "Build file '/home/user/gradle/build.gradle'"
               + "Total time: 4.622 secs";

       parseOutputTest(outputText, new FileLink(new File("/home/user/gradle/build.gradle"), 63, 104, 431), new FileLink(new File("/home/user/gradle/build.gradle"), 152, 182, -1));
   }


   /**
    This attempts to find a single ant:checkstyle error that occurs when the checkstyle fails. This error consists of
    the file, the line of the problem, and the cause.
    */
   public void testCheckstyleSingleErrorOutput() {
       String outputText = ":processResources\n"
               + ":idea-plugins:compile\n"
               + ":compile\n"
               + ":copyNativeLibs\n"
               + ":distributionDiskResources SKIPPED\n"
               + ":installDiskResources SKIPPED\n"
               + "[ant:checkstyle] /home/user/gradle/subprojects/gradle-core/src/main/groovy/org/gradle/util/exec/ExecHandleShutdownHookAction.java:38: 'if' construct must use '{}'s."
               + "FAILURE: Build failed with an exception.\n"
               + "\n"
               + "* What went wrong:\n"
               + "blah blah blah\n"
               + "Total time: 4.622 secs";

       parseOutputTest(outputText, new FileLink(new File("/home/user/gradle/subprojects/gradle-core/src/main/groovy/org/gradle/util/exec/ExecHandleShutdownHookAction.java"), 147, 262, 38));
   }

   /**
    This attemps to find the checkstyle error report file. This file will list all errors and where they are located.
    The file link we're looking for just has the file's path
    */
   public void testCheckstyleReportErrorFile() {
       String outputText = ":processResources\n"
               + ":plugins:compile\n"
               + ":compile\n"
               + ":copyNativeLibs\n"
               + ":distributionDiskResources SKIPPED\n"
               + ":installDiskResources SKIPPED\n"
               + "FAILURE: Build failed with an exception.\n"
               + "\n"
               + "* What went wrong:\n"
               + "Cause: Checkstyle check violations were found in main Java source. See the report at /home/user/gradle/subprojects/gradle-core/build/checkstyle/main.xml\n"
               + "Total time: 4.622 secs"
               + "blah blah blah";

       parseOutputTest(outputText, new FileLink(new File("/home/user/gradle/subprojects/gradle-core/build/checkstyle/main.xml"), 271, 338, -1));
   }

   /**
    Tests that any HTML reports (prefixed with "See the report at ") are found.
    */
   public void testHTMLReport() {
       String outputText = "* What went wrong:\n"
               + "Execution failed for task ':ui:codenarcTest'.\n"
               + "Cause: CodeNarc check violations were found in test Groovy source. See the report at /home/user/gradle/subprojects/gradle-ui/build/reports/codenarc/test.html.\n"
               + "\n"
               + "* Try:\n"
               + "Run with -s or -d option to get more details. Run with -S option to get the full (very verbose) stacktrace.";

       parseOutputTest(outputText, new FileLink(new File("/home/user/gradle/subprojects/gradle-ui/build/reports/codenarc/test.html"), 150, 222, -1));
   }

   /**
    This tests a bug I discovered while coding this where the path matched by the Note: would start with the space
    before the '/', but the Build file would start at the '/'. This test is here because my first attempt
    I tracked the problem down to BasicFileLinkDefintion. It was assuming the file started at immediately after
    the prefix. This was incorrect. There may be spaces between the prefix and the file (and I didn't want to put
    spaces in the prefix). So I added BasicFileLinkDefintion.getStartOfFile() to skip over these spaces.
    */
   public void testOffByOneCharacterBug() {
       String outputText = "[ant:javac] Note: /home/user/modules/f1j/src/main/java/com/thing/DesignerManager.java uses or overrides a deprecated API.\n"
               + "Build file '/home/user/modules/build.gradle'";

       parseOutputTest(outputText, new FileLink(new File("/home/user/modules/f1j/src/main/java/com/thing/DesignerManager.java"), 18, 85, -1),
               new FileLink(new File("/home/user/modules/build.gradle"), 134, 165, -1));
   }

   /**
      This test that test reports file is found. This is a special case since we're not given
      the actual file path and instead given only its parent path. The code assumes the report file
      is present.
      */
   public void testFailedTestsReportFile() {
       String outputText = "FAILURE: Build failed with an exception.\n"
               + "\n"
               + "* Where:\n"
               + "Build file '/home/user/gradle/gradle/build.gradle'\n"
               + "\n"
               + "* What went wrong:\n"
               + "Execution failed for task ':integTest'.\n"
               + "Cause: There were failing tests. See the report at /home/user/gradle/gradle/build/reports/tests.\n"
               + "Total time: 4.622 secs\n"
               + "blah blah blah";

       parseOutputTest(outputText, new FileLink(new File("/home/user/gradle/gradle/build.gradle"), 63, 100, -1),
               new FileLink(new File("/home/user/gradle/gradle/build/reports/tests/index.html"), 213, 258, -1));
   }

   /**
    This tests for multiple files found in a single output.
    */
   public void testMultiples() {
       String outputText = ":distributionDiskResources SKIPPED\n"
               + ":installDiskResources SKIPPED\n"
               + ":idea-plugins:ideagradle:compileJava\n\n"
               + "[ant:checkstyle] /home/user/modules/gradle/subprojects/gradle-core/src/main/groovy/org/gradle/util/exec/ExecHandleShutdownHookAction.java:38: 'if' construct must use '{}'s.\n"
               + "Note: /home/user/modules/gradle/subprojects/gradle-core/src/test/groovy/org/gradle/integtests/DistributionIntegrationTestRunner.java uses or overrides a deprecated API.\n"
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

            parseOutputTest(outputText, new FileLink(new File("/home/user/modules/gradle/subprojects/gradle-core/src/main/groovy/org/gradle/util/exec/ExecHandleShutdownHookAction.java"), 120, 243, 38),
                    new FileLink(new File("/home/user/modules/gradle/subprojects/gradle-core/src/test/groovy/org/gradle/integtests/DistributionIntegrationTestRunner.java"), 282, 408, -1),
                    new FileLink(new File("/home/user/modules/gradle/subprojects/gradle-core/build/checkstyle/main.xml"), 531, 606, -1),
                    new FileLink(new File("/home/user/modules/gradle/subprojects/gradle-ui/ui.gradle"), 622, 679, -1),
                    new FileLink(new File("/home/user/modules/gradle/subprojects/gradle-ui/build/reports/codenarc/test.html"), 832, 912, -1));
   }

    /**
     * This tests that different cases of file extensions do not prevent files from being found. I
     * want to explicitly test this because I had to explicitly search for case insensitive matches.
     */
   public void testFileExtensionCaseSensitivity() {
       String outputText = ":distributionDiskResources SKIPPED\n"
               + ":installDiskResources SKIPPED\n"
               + "/home/user/files/Thing.java:38: 'if' construct must use '{}'s.\n"
               + "/home/user/files/Thing2.JAVA:929: cannot find symbol\n"
               + "/home/user/files/Thing3.JaVa:77: incompatible types\n"
               + "* Try:\n"
               + "Run with -s or -d option to get more details. Run with -S option to get the full (very verbose) stacktrace.";

       parseOutputTest(outputText, new FileLink(new File("/home/user/files/Thing.java"), 65, 95, 38),
               new FileLink(new File("/home/user/files/Thing2.JAVA"), 128, 160, 929),
               new FileLink(new File("/home/user/files/Thing3.JaVa"), 181, 212, 77));
   }
}
