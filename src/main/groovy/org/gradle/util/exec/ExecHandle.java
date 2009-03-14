package org.gradle.util.exec;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * @author Tom Eyckmans
 */
public interface ExecHandle {

    File getDirectory();

    String getCommand();

    List<String> getArguments();

    Map<String, String> getEnvironment();

    ExecHandleState getState();

    int getExitCode();

    Throwable getFailureCause();

    void start();

    void abort();

    ExecHandleState waitForFinish();

    ExecHandleState startAndWaitForFinish();
}
