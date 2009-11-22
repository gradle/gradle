package org.gradle.util.shutdown;

import java.util.List;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Tom Eyckmans
 */
public class ShutdownHookActionRegister {
    private static final ShutdownHookActionRegister INSTANCE = new ShutdownHookActionRegister();

    private final List<ShutdownHookAction> shutdownHookActions;

    private ShutdownHookActionRegister()
    {
        shutdownHookActions = new CopyOnWriteArrayList<ShutdownHookAction>();
    }

    public static void addShutdownHookAction(ShutdownHookAction shutdownHookAction)
    {
        if ( shutdownHookAction != null )
            INSTANCE.shutdownHookActions.add(shutdownHookAction);
    }

    public static void removeShutdownHookAction(ShutdownHookAction shutdownHookAction) {
        if ( shutdownHookAction != null )
            INSTANCE.shutdownHookActions.remove(shutdownHookAction);
    }

    static List<ShutdownHookAction> getSHutHookActions()
    {
        return Collections.unmodifiableList(INSTANCE.shutdownHookActions);
    }
}
