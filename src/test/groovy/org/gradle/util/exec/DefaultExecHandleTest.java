package org.gradle.util.exec;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import org.apache.tools.ant.util.JavaEnvUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class DefaultExecHandleTest {

    private DefaultExecHandle execHandle;

    @Test
    public void testJavadocVersion() throws IOException {
        StreamWriterExecOutputHandle outHandle = new StreamWriterExecOutputHandle(System.out, true);
        StreamWriterExecOutputHandle errHandle = new StreamWriterExecOutputHandle(System.err, true);

        System.out.println("Javadoc executable = " + JavaEnvUtils.getJdkExecutable("javadoc"));

        execHandle = new DefaultExecHandle(
                new File("./src/main/groovy"),
                JavaEnvUtils.getJdkExecutable("javadoc"),
                Arrays.asList("-verbose", "-d", new File("tmp/javadocTmpOut").getAbsolutePath(), "org.gradle"), 0,
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
                new File("./src/main/groovy"),
                JavaEnvUtils.getJdkExecutable("javadoc"),
                Arrays.asList("-verbose", "-d", new File("tmp/javadocTmpOut").getAbsolutePath(), "org.gradle"), 0,
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
}
