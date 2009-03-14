package org.gradle.util.exec;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

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
        
        execHandle = new DefaultExecHandle(
                new File("./src/main/groovy"),
                ""+System.getProperty("java.home")+"/../bin/javadoc",
                Arrays.asList("-verbose", "-d", "/tmp/javadocTmpOut", "org.gradle"),
                System.getenv(),
                100,
                outHandle,
                errHandle,
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
        execHandle = new DefaultExecHandle(
                new File("./src/main/groovy"),
                ""+System.getProperty("java.home")+"/../bin/javadoc",
                Arrays.asList("-verbose", "-d", "/tmp/javadocTmpOut", "org.gradle"),
                System.getenv(),
                100,
                outHandle,
                errHandle,
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
