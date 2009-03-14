package org.gradle.util.exec;

import org.gradle.util.ThreadUtils;

import java.io.File;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * @author Tom Eyckmans
 */
public class DefaultExecHandle implements ExecHandle {
    private final File directory;
    private final String command;
    private final List<String> arguments;
    private final Map<String, String> environment;
    private final long keepWaitingTimeout;

    private final ExecOutputHandle standardOutputHandle;
    private final ExecOutputHandle errorOutputHandle;

    private final Lock stateLock;
    private ExecHandleState state;

    private final Lock execHandleRunLock;
    private ExecHandleRunner execHandleRunner;
    private ExecutorService threadPool;

    private final Lock endStateInfoLock;
    private int exitCode;
    private Throwable failureCause;

    private final List<ExecHandleListener> listeners = new CopyOnWriteArrayList<ExecHandleListener>();

    DefaultExecHandle(File directory, String command, List<String> arguments, Map<String, String> environment, long keepWaitingTimeout, ExecOutputHandle standardOutputHandle, ExecOutputHandle errorOutputHandle, List<ExecHandleListener> listeners) {
        this.directory = directory;
        this.command = command;
        this.arguments = arguments;
        this.environment = environment;
        this.keepWaitingTimeout = keepWaitingTimeout;
        this.standardOutputHandle = standardOutputHandle;
        this.errorOutputHandle = errorOutputHandle;
        this.stateLock = new ReentrantLock();
        this.state = ExecHandleState.INIT;
        this.execHandleRunLock = new ReentrantLock();
        this.endStateInfoLock = new ReentrantLock();
        if ( listeners != null && !listeners.isEmpty() )
            this.listeners.addAll(listeners);
    }

    public File getDirectory() {
        return directory;
    }

    public String getCommand() {
        return command;
    }

    public List<String> getArguments() {
        return Collections.unmodifiableList(arguments);
    }

    public Map<String, String> getEnvironment() {
        return Collections.unmodifiableMap(environment);
    }

    public long getKeepWaitingTimeout() {
        return keepWaitingTimeout;
    }

    public ExecOutputHandle getStandardOutputHandle() {
        return standardOutputHandle;
    }

    public ExecOutputHandle getErrorOutputHandle() {
        return errorOutputHandle;
    }

    public ExecHandleState getState() {
        stateLock.lock();
        try {
            return state;
        }
        finally {
            stateLock.unlock();
        }
    }

    private void setState(ExecHandleState state) {
        stateLock.lock();
        try {
            this.state = state;
        }
        finally {
            stateLock.unlock();
        }
    }

    private boolean stateEquals(ExecHandleState state) {
        stateLock.lock();
        try {
            return this.state.equals(state);
        }
        finally {
            stateLock.unlock();
        }
    }

    private boolean stateIn(ExecHandleState ... states) {
        stateLock.lock();
        try {
            return Arrays.asList(states).contains(this.state);
        }
        finally {
            stateLock.unlock();
        }
    }

    public int getExitCode() {
        if ( stateIn(ExecHandleState.SUCCEEDED, ExecHandleState.FAILED) )
            throw new IllegalStateException("not in finished or failed state!");
        endStateInfoLock.lock();
        try {
            return exitCode;
        }
        finally {
            endStateInfoLock.unlock();
        }
    }

    public Throwable getFailureCause() {
        if ( !stateEquals(ExecHandleState.FAILED) )
            throw new IllegalStateException("not in failed state!");
        return failureCause;
    }

    private void setEndStateInfo(ExecHandleState state, int exitCode, Throwable failureCause) {
        endStateInfoLock.lock();
        try {
            setState(state);
            this.exitCode = exitCode;
            this.failureCause = failureCause;
        }
        finally {
            endStateInfoLock.unlock();
        }
    }

    public void start() {
        if ( stateEquals(ExecHandleState.STARTED) ) throw new IllegalStateException("already started!");

        execHandleRunLock.lock();
        try {
            exitCode = -1;
            failureCause = null;

            threadPool = Executors.newFixedThreadPool(3);
            execHandleRunner = new ExecHandleRunner(this, threadPool);

            threadPool.execute(execHandleRunner);
        }
        finally {
            execHandleRunLock.unlock();
        }
    }

    public void abort() {
        if ( !stateEquals(ExecHandleState.STARTED) ) throw new IllegalStateException("not in started state!");
        execHandleRunLock.lock();
        try {
            this.execHandleRunner.stopWaiting();

            waitForFinish();
        }
        finally {
            execHandleRunLock.unlock();
        }
    }

    public ExecHandleState waitForFinish() {
        if ( !stateEquals(ExecHandleState.STARTED) ) throw new IllegalStateException("not in started state!");
        execHandleRunLock.lock();
        try {
            shutdownThreadPool();
        }
        finally {
            execHandleRunLock.unlock();
        }

        return getState();
    }

    private void shutdownThreadPool() {
        ThreadUtils.shutdown(threadPool);
    }

    public ExecHandleState startAndWaitForFinish() {
        if ( stateEquals(ExecHandleState.STARTED) ) throw new IllegalStateException("already started!");
        execHandleRunLock.lock();
        try {
            start();

            boolean proceed = false;
            while ( !proceed ) {
                switch ( getState() ) { // check on snapshot of state as to not block an external state poller
                    case INIT:
                        /*
                        * As long as in state INIT not all the output consumtion threads are
                        * started, so we wait.
                        */
                        Thread.yield();
                        break;
                    default :
                        proceed = true;
                }
            }

            // check on snapshot of state as to not block an external state poller
            if ( getState() == ExecHandleState.STARTED ) {
                waitForFinish();
            }
            else {
                // possible that the process terminated immediately then there is no need to wait and we can shutdown the threadPool
                // this is the cleanest way otherwise we need to introduce a possible race condition in waitForFinish:
                // call start ( external process terminates directly ), call waitForFinish would never return now it throws an exception
                // when the process is not started.
                shutdownThreadPool();
            }
        }
        finally {
            execHandleRunLock.unlock();
        }

        return getState();
    }

    void started() {
        setState(ExecHandleState.STARTED);
        new Thread(new StartedNotifier(this, listeners)).start();
    }

    void finished(int exitCode) {
        if ( exitCode != 0 ) {
            setEndStateInfo(ExecHandleState.SUCCEEDED, exitCode, new RuntimeException("exitCode != 0!"));
            new Thread(new FailedNotifier(this, listeners)).start();
        }
        else {
            setEndStateInfo(ExecHandleState.SUCCEEDED, 0, null);
            new Thread(new FinishedNotifier(this, listeners)).start();
        }
    }

    void aborted() {
        setState(ExecHandleState.ABORTED);
        new Thread(new AbortedNotifier(this, listeners)).start();
    }

    void failed(Throwable failureCause) {
        setEndStateInfo(ExecHandleState.FAILED, -1, failureCause);
        new Thread(new FailedNotifier(this, listeners)).start();
    }

    public void addListeners(ExecHandleListener ... listeners) {
        if ( listeners == null ) throw new IllegalArgumentException("listeners == null!");
        this.listeners.addAll(Arrays.asList(listeners));
    }

    public void removeListeners(ExecHandleListener ... listeners) {
        if ( listeners == null ) throw new IllegalArgumentException("listeners == null!");
        this.listeners.removeAll(Arrays.asList(listeners));
    }

    private abstract class Notifier implements Runnable
    {
        private final ExecHandle execHandle;
        private final List<ExecHandleListener> listeners;

        public Notifier(final ExecHandle execHandle, final List<ExecHandleListener> currentListeners) {
            this.execHandle = execHandle;
            this.listeners = new ArrayList<ExecHandleListener>(currentListeners);
        }

        public void run() {
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

    private class StartedNotifier extends Notifier {
        public StartedNotifier(final ExecHandle execHandle, final List<ExecHandleListener> currentListeners) {
            super(execHandle, currentListeners);
        }

        protected boolean notifyListener(ExecHandle execHandle, ExecHandleListener listener) {
            listener.executionStarted(execHandle);
            return true;
        }
    }

    private class FinishedNotifier extends Notifier {
        public FinishedNotifier(final ExecHandle execHandle, final List<ExecHandleListener> currentListeners) {
            super(execHandle, currentListeners);
        }

        protected boolean notifyListener(ExecHandle execHandle, ExecHandleListener listener) {
            listener.executionFinished(execHandle);
            return true;
        }
    }

    private class AbortedNotifier extends Notifier {
        public AbortedNotifier(final ExecHandle execHandle, final List<ExecHandleListener> currentListeners) {
            super(execHandle, currentListeners);
        }

        protected boolean notifyListener(ExecHandle execHandle, ExecHandleListener listener) {
            listener.executionAborted(execHandle);
            return true;
        }
    }

    private class FailedNotifier extends Notifier {
        public FailedNotifier(final ExecHandle execHandle, final List<ExecHandleListener> currentListeners) {
            super(execHandle, currentListeners);
        }

        protected boolean notifyListener(ExecHandle execHandle, ExecHandleListener listener) {
            listener.executionFailed(execHandle);
            return true;
        }
    }
}
