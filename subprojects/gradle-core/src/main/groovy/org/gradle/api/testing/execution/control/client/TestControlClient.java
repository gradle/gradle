package org.gradle.api.testing.execution.control.client;

import org.gradle.api.testing.execution.control.refork.ReforkDecisionContext;
import org.gradle.api.testing.fabric.TestClassProcessResult;

/**
 * @author Tom Eyckmans
 */
public interface TestControlClient {

    void reportStarted();

    void reportStopped();

    void requestNextControlMessage(TestClassProcessResult previousProcessTestResult, ReforkDecisionContext reforkDecisionContext);
}
