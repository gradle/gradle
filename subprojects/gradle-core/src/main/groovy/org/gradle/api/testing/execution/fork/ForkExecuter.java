package org.gradle.api.testing.execution.fork;

import java.util.List;

/**
 * Interface with methods expected to be supported by the controller that is instanciated by the ForkLaunchMain.
 *
 * @author Tom Eyckmans
 */
public interface ForkExecuter {
    void setSharedClassLoader(ClassLoader sharedClassLoader);

    void setControlClassLoader(ClassLoader controlClassLoader);

    void setSandboxClassLoader(ClassLoader sandboxClassLoader);

    void setArguments(List<String> arguments);

    void execute();
}
