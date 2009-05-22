package org.gradle.api.changedetection.state;

import org.gradle.api.io.DefaultIoFactory;
import org.gradle.api.io.IoFactory;
import org.gradle.api.changedetection.digest.DigestObjectFactory;
import org.gradle.api.changedetection.digest.DigesterCache;
import org.gradle.api.changedetection.digest.DigesterUtil;
import org.gradle.util.queues.BlockingQueueItemProducer;

import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class DefaultDirectoryStateChangeDetecterBuilder {
    private File rootProjectDirectory;
    private File directoryToProcess;
    private File dotGradleStatesDirectory;
    private int stateChangeEventQueueSize = 50;
    private long stateChangeEventQueuePollTimeout = 100L;
    private DigesterUtil digesterUtil;
    private StateComparator stateComparator;

    public DefaultDirectoryStateChangeDetecterBuilder() {
        fileMetaDataOnlyHashing();
        allChangesStateComparator();
    }

    public void setRootProjectDirectory(File rootProjectDirectory) {
        if ( rootProjectDirectory == null ) throw new IllegalArgumentException("rootProjectDirectory is null!");
        if ( !rootProjectDirectory.exists() ) throw new IllegalArgumentException("rootProjectDirectory does not exists!");
        if ( !rootProjectDirectory.isDirectory() ) throw new IllegalArgumentException("rootProjectDirectory is not a directory!");

        this.rootProjectDirectory = rootProjectDirectory;
    }

    public DefaultDirectoryStateChangeDetecterBuilder rootProjectDirectory(File rootProjectDirectory) {
        setRootProjectDirectory(rootProjectDirectory);

        return this;
    }

    public void setDirectoryToProcess(File directoryToProcess) {
        if ( directoryToProcess == null ) throw new IllegalArgumentException("directoryToProcess is null!");
        if ( !directoryToProcess.exists() ) throw new IllegalArgumentException("directoryToProcess does not exists!");
        if ( !directoryToProcess.isDirectory() ) throw new IllegalArgumentException("directoryToProcess is not a directory!");

        this.directoryToProcess = directoryToProcess;
    }

    public DefaultDirectoryStateChangeDetecterBuilder directoryToProcess(File directoryToProcess) {
        setDirectoryToProcess(directoryToProcess);

        return this;
    }

    public void setDotGradleStatesDirectory(File dotGradleStatesDirectory) {
        this.dotGradleStatesDirectory = dotGradleStatesDirectory;
    }

    public DefaultDirectoryStateChangeDetecterBuilder dotGradleStatesDirectory(File dotGradleStatesDirectory) {
        setDotGradleStatesDirectory(dotGradleStatesDirectory);
        return this;
    }

    public void setStateChangeEventQueueSize(int stateChangeEventQueueSize) {
        if ( stateChangeEventQueueSize < 1 ) throw new IllegalArgumentException("stateChangeEventQueueSize < 1!");
        this.stateChangeEventQueueSize = stateChangeEventQueueSize;
    }

    public DefaultDirectoryStateChangeDetecterBuilder stateChangeEventQueueSize(int stateChangeEventQueueSize) {
        setStateChangeEventQueueSize(stateChangeEventQueueSize);
        return this;
    }

    public void setStateChangeEventQueuePollTimeout(long stateChangeEventQueuePollTimeout) {
        if ( stateChangeEventQueuePollTimeout <= 0L ) throw new IllegalArgumentException("stateChangeEventQueuePollTimeout <= 0!");
        this.stateChangeEventQueuePollTimeout = stateChangeEventQueuePollTimeout;
    }

    public DefaultDirectoryStateChangeDetecterBuilder stateChangeEventQueuePollTimeout(long stateChangeEventQueuePollTimeout) {
        this.stateChangeEventQueuePollTimeout = stateChangeEventQueuePollTimeout;
        return this;
    }

    public DefaultDirectoryStateChangeDetecterBuilder fileMetaDataOnlyHashing() {
        digesterUtil = DigestObjectFactory.createMetaDigesterUtil();
        return this;
    }

    public DefaultDirectoryStateChangeDetecterBuilder fileMetaDataAndContentHashing() {
        digesterUtil = DigestObjectFactory.createMetaContentDigesterUtil();
        return this;
    }

    public DefaultDirectoryStateChangeDetecterBuilder dirChangedStateComparator() {
        stateComparator = new DirChangedStateComparator();
        return this;
    }

    public DefaultDirectoryStateChangeDetecterBuilder allChangesStateComparator() {
        stateComparator = new AllChangesStateComparator();
        return this;
    }

    public DirectoryStateChangeDetecter getDirectoryStateChangeDetecter() {
        if ( rootProjectDirectory == null ) throw new IllegalArgumentException("rootProjectDirectory is null!");
        if ( !rootProjectDirectory.exists() ) throw new IllegalArgumentException("rootProjectDirectory does not exists!");
        if ( !rootProjectDirectory.isDirectory() ) throw new IllegalArgumentException("rootProjectDirectory is not a directory!");

        if ( directoryToProcess == null ) throw new IllegalArgumentException("directoryToProcess is null!");
        if ( !directoryToProcess.exists() ) throw new IllegalArgumentException("directoryToProcess does not exists!");
        if ( !directoryToProcess.isDirectory() ) throw new IllegalArgumentException("directoryToProcess is not a directory!");

        final IoFactory ioFactory = new DefaultIoFactory();
        final DigesterCache digesterCache = DigestObjectFactory.createShaDigesterCache();
        final StateFileUtil stateFileUtil = new StateFileUtil(rootProjectDirectory, directoryToProcess, dotGradleStatesDirectory, digesterCache.getDigesterFactory(), ioFactory);
        final DirectoryStateBuilder directoryStateBuilder = new DirectoryStateBuilder(stateFileUtil);
        final DirectoryListFileCreator directoryListFileCreator = new DirectoryListFileCreator(stateFileUtil);
        final BlockingQueue<StateChangeEvent> stateChangeEventQueue = new ArrayBlockingQueue<StateChangeEvent>(stateChangeEventQueueSize);
        final BlockingQueueItemProducer<StateChangeEvent> changeProcessorEventProducer = new BlockingQueueItemProducer<StateChangeEvent>(stateChangeEventQueue, stateChangeEventQueuePollTimeout, TimeUnit.MILLISECONDS);
        final List<DirectoryStateDigestComparator> directoryStateDigestComparators = new ArrayList<DirectoryStateDigestComparator>();
        final StateChangeEventFactory stateChangeEventFactory = new StateChangeEventFactory();
        final StateFileChangeListenerUtil stateFileChangeListenerUtil = new StateFileChangeListenerUtil(changeProcessorEventProducer, stateChangeEventFactory);
        
        return new DefaultDirectoryStateChangeDetecter(
                directoryToProcess,
                ioFactory,
                directoryStateBuilder,
                digesterCache,
                digesterUtil,
                directoryListFileCreator,
                stateFileUtil,
                stateChangeEventQueue,
                changeProcessorEventProducer,
                directoryStateDigestComparators,
                stateFileChangeListenerUtil,
                stateComparator);
    }
}
