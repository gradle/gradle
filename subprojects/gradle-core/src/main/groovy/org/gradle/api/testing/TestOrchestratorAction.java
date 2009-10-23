package org.gradle.api.testing;

/**
 * @author Tom Eyckmans
 */
public interface TestOrchestratorAction {
    void execute(TestOrchestratorContext context);
}
