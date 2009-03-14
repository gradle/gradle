package org.gradle.util.exec;

import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import org.apache.tools.ant.util.JavaEnvUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class DefaultExecHandleTest {

    private DefaultExecHandle execHandle;

    private final File tmpDir = new File("tmp");
    private final File sourceDir = new File(tmpDir, "src/main/groovy");

    @Before
    public void setUp() throws IOException {
        sourceDir.mkdirs();

        final File gradlePackage = new File(sourceDir, "org/gradle");
        gradlePackage.mkdirs();

        final File testSourceFile = new File(gradlePackage, "Test.java");
        final String newLine = System.getProperty("line.separator");

        FileUtils.writeStringToFile(testSourceFile,
                "package org.gradle; " + newLine +
                "/**" + newLine +
                " *@author Test Author" + newLine +
                " */" + newLine +
                "public class Test { }"
        );
    }

    @Test
    public void testJavadocVersion() throws IOException {
        StreamWriterExecOutputHandle outHandle = new StreamWriterExecOutputHandle(System.out, true);
        StreamWriterExecOutputHandle errHandle = new StreamWriterExecOutputHandle(System.err, true);

        System.out.println("Javadoc executable = " + JavaEnvUtils.getJdkExecutable("javadoc"));



        execHandle = new DefaultExecHandle(
                tmpDir,
                JavaEnvUtils.getJdkExecutable("javadoc"),
                Arrays.asList(
                        "-verbose",
                        "-d", new File("tmp/javadocTmpOut").getAbsolutePath(),
                        "-sourcepath", "src/main/groovy",
                        "org.gradle"), 0,
                System.getenv(),
                100,
                outHandle,
                errHandle,
                new DefaultExecHandleNotifierFactory(),
                null
        );

        final ExecHandleState endState = execHandle.startAndWaitForFinish();

        if ( endState == ExecHandleState.FAILED ) {
            execHandle.getFailureCause().printStackTrace();
        }

        assertEquals(ExecHandleState.SUCCEEDED, endState);
    }

    @Test
    public void testAbort() throws IOException {
        StreamWriterExecOutputHandle outHandle = new StreamWriterExecOutputHandle(System.out, true);
        StreamWriterExecOutputHandle errHandle = new StreamWriterExecOutputHandle(System.err, true);

        System.out.println("Javadoc executable = " + JavaEnvUtils.getJdkExecutable("javadoc"));

        execHandle = new DefaultExecHandle(
                tmpDir,
                JavaEnvUtils.getJdkExecutable("javadoc"),
                Arrays.asList(
                        "-verbose",
                        "-d", new File("tmp/javadocTmpOut").getAbsolutePath(),
                        "-sourcepath", "src/main/groovy",
                        "org.gradle"), 0,
                System.getenv(),
                100,
                outHandle,
                errHandle,
                new DefaultExecHandleNotifierFactory(), 
                null
        );

        execHandle.start();
        execHandle.abort();

        final ExecHandleState endState = execHandle.waitForFinish();

        if ( endState == ExecHandleState.FAILED ) {
            execHandle.getFailureCause().printStackTrace();
        }

        assertEquals(ExecHandleState.ABORTED, endState);
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(tmpDir);
    }
}
