package org.gradle.api.testing.execution.control.server;

import org.gradle.api.testing.execution.fork.ForkControl;
import org.gradle.api.testing.execution.Pipeline;

/**
 * @author Tom Eyckmans
 */
public class TestServerClientHandleFactory {
    private final ForkControl forkControl;

    public TestServerClientHandleFactory(ForkControl forkControl) {
        if ( forkControl == null ) throw new IllegalArgumentException("forkControl == null!");

        this.forkControl = forkControl;
    }

    public TestServerClientHandle createTestServerClientHandle(Pipeline pipeline, int forkId) {
        return new TestServerClientHandle(pipeline, forkId, forkControl);
    }
}
