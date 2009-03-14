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
    private final int normalTerminationExitCode;
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

    private final ExecHandleNotifierFactory notifierFactory;
    private final List<ExecHandleListener> listeners = new CopyOnWriteArrayList<ExecHandleListener>();

    DefaultExecHandle(File directory, String command, List<String> arguments, int normalTerminationExitCode, Map<String, String> environment, long keepWaitingTimeout, ExecOutputHandle standardOutputHandle, ExecOutputHandle errorOutputHandle, ExecHandleNotifierFactory notifierFactory, List<ExecHandleListener> listeners) {
        this.directory = directory;
        this.command = command;
        this.arguments = arguments;
        this.normalTerminationExitCode = normalTerminationExitCode;
        this.environment = environment;
        this.keepWaitingTimeout = keepWaitingTimeout;
        this.standardOutputHandle = standardOutputHandle;
        this.errorOutputHandle = errorOutputHandle;
        this.stateLock = new ReentrantLock();
        this.state = ExecHandleState.INIT;
        this.execHandleRunLock = new ReentrantLock();
        this.endStateInfoLock = new ReentrantLock();
        this.notifierFactory = notifierFactory;
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
            throw new IllegalStateException("not in succeeded or failed state!");
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
            setState(ExecHandleState.INIT);

            exitCode = -1;
            failureCause = null;

            threadPool = Executors.newFixedThreadPool(3);
            execHandleRunner = new ExecHandleRunner(this, threadPool);

            threadPool.execute(execHandleRunner);

            while ( getState() == ExecHandleState.INIT ) {
                Thread.yield();
            }
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
        }
        finally {
            execHandleRunLock.unlock();
        }
    }

    public ExecHandleState waitForFinish() {
        execHandleRunLock.lock();
        try {
            ThreadUtils.awaitTermination(threadPool);
        }
        finally {
            execHandleRunLock.unlock();
        }

        return getState();
    }

    private void shutdownThreadPool() {
        ThreadUtils.run(new Runnable(){

            public void run() {
                ThreadUtils.shutdown(threadPool);
            }
        });
    }

    public ExecHandleState startAndWaitForFinish() {
        if ( stateEquals(ExecHandleState.STARTED) ) throw new IllegalStateException("already started!");
        execHandleRunLock.lock();
        try {
            start();

            waitForFinish();
        }
        finally {
            execHandleRunLock.unlock();
        }

        return getState();
    }

    void started() {
        setState(ExecHandleState.STARTED);
        ThreadUtils.run(notifierFactory.createStartedNotifier(this));
    }

    void finished(int exitCode) {
        if ( exitCode != normalTerminationExitCode ) {
            setEndStateInfo(ExecHandleState.FAILED, exitCode, new RuntimeException("exitCode != "+normalTerminationExitCode+"!"));
            shutdownThreadPool();
            ThreadUtils.run(notifierFactory.createFailedNotifier(this));
        }
        else {
            setEndStateInfo(ExecHandleState.SUCCEEDED, 0, null);
            shutdownThreadPool();
            ThreadUtils.run(notifierFactory.createSucceededNotifier(this));
        }
    }

    void aborted() {
        setState(ExecHandleState.ABORTED);
        shutdownThreadPool();
        ThreadUtils.run(notifierFactory.createAbortedNotifier(this));
    }

    void failed(Throwable failureCause) {
        setEndStateInfo(ExecHandleState.FAILED, -1, failureCause);
        shutdownThreadPool();
        ThreadUtils.run(notifierFactory.createFailedNotifier(this));
    }

    public void addListeners(ExecHandleListener ... listeners) {
        if ( listeners == null ) throw new IllegalArgumentException("listeners == null!");
        this.listeners.addAll(Arrays.asList(listeners));
    }

    public void removeListeners(ExecHandleListener ... listeners) {
        if ( listeners == null ) throw new IllegalArgumentException("listeners == null!");
        this.listeners.removeAll(Arrays.asList(listeners));
    }

    public List<ExecHandleListener> getListeners()
    {
        return Collections.unmodifiableList(listeners);
    }
}
