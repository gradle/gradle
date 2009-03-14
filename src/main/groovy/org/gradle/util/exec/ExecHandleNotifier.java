package org.gradle.util.exec;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author Tom Eyckmans
 */
public abstract class ExecHandleNotifier implements Runnable {
    private final ExecHandle execHandle;
    private final List<ExecHandleListener> listeners;

    public ExecHandleNotifier(final ExecHandle execHandle) {
        this.execHandle = execHandle;
        this.listeners = new ArrayList<ExecHandleListener>(execHandle.getListeners());
    }

    public final void run() {
        final Iterator<ExecHandleListener> listenersIt = listeners.iterator();
        boolean keepNotifing = true;
        while ( keepNotifing && listenersIt.hasNext() ) {
            final ExecHandleListener listener = listenersIt.next();
            try {
                keepNotifing = notifyListener(execHandle, listener);
            }
            catch ( Throwable t ) {
                // ignore
                // TODO listenerNotificationFailureHandle.listenerFailed(execHandle, listener)
                // TODO may be interesting for e.g. when an log writer fails remove it and add an email sender
                // TODO but for now ignore it
            }
        }
    }

    protected abstract boolean notifyListener(ExecHandle execHandle, ExecHandleListener listener);
}
