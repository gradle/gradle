package org.gradle.api.changedetection.state;

import org.gradle.api.changedetection.digest.*;
import org.gradle.api.changedetection.ChangeProcessor;
import org.gradle.api.GradleException;
import org.gradle.api.io.IoFactory;
import org.gradle.api.io.DefaultIoFactory;
import org.gradle.util.ThreadUtils;
import org.gradle.util.Clock;
import org.gradle.util.queues.BlockingQueueItemProducer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author Tom Eyckmans
 */
class DefaultDirectoryStateChangeDetecter implements DirectoryStateChangeDetecter {

    private final File directoryToProcess;
    private final IoFactory ioFactory;
    private final DirectoryStateBuilder directoryStateBuilder;
    private final DigesterCache digesterCache;
    private final DigesterUtil digesterUtil;
    private final DirectoryListFileCreator directoryListFileCreator;
    private final StateFileUtil stateFileUtil;
    private final BlockingQueue<StateChangeEvent> stateChangeEventQueue;
    private final BlockingQueueItemProducer<StateChangeEvent> changeProcessorEventProducer;
    private final List<DirectoryStateDigestComparator> directoryStateDigestComparators;
    private ExecutorService threadPool;
    private final StateFileChangeListenerUtil stateFileChangeListenerUtil;
    private final StateComparator stateComparator;

    DefaultDirectoryStateChangeDetecter(
            final File directoryToProcess, IoFactory ioFactory, DirectoryStateBuilder directoryStateBuilder, DigesterCache digesterCache, DigesterUtil digesterUtil, DirectoryListFileCreator directoryListFileCreator, StateFileUtil stateFileUtil, BlockingQueue<StateChangeEvent> stateChangeEventQueue, BlockingQueueItemProducer<StateChangeEvent> changeProcessorEventProducer, List<DirectoryStateDigestComparator> directoryStateDigestComparators, StateFileChangeListenerUtil stateFileChangeListenerUtil, StateComparator stateComparator) {
        if ( directoryToProcess == null ) throw new IllegalArgumentException("directoryToProcess is null!");
        if ( !directoryToProcess.exists() ) throw new IllegalArgumentException("directoryToProcess does not exists!");
        if ( !directoryToProcess.isDirectory() ) throw new IllegalArgumentException("directoryToProcess is not a directory!");

        this.directoryToProcess = directoryToProcess;
        this.ioFactory = ioFactory;
        this.directoryStateBuilder = directoryStateBuilder;
        this.digesterCache = digesterCache;
        this.digesterUtil = digesterUtil;
        this.directoryListFileCreator = directoryListFileCreator;
        this.stateFileUtil = stateFileUtil;
        this.stateChangeEventQueue = stateChangeEventQueue;
        this.changeProcessorEventProducer = changeProcessorEventProducer;
        this.directoryStateDigestComparators = directoryStateDigestComparators;
        this.stateFileChangeListenerUtil = stateFileChangeListenerUtil;
        this.stateComparator = stateComparator;
    }

    public File getDirectoryToProcess() {
        return directoryToProcess;
    }

    public void detectChanges(ChangeProcessor changeProcessor) {
        Clock c = new Clock();
        try {
            int lowestLevel = 0;
            try {
                lowestLevel = directoryListFileCreator.createDirectoryListFiles(directoryToProcess);
            }
            catch ( IOException e ) {
                throw new GradleException("failed to create directory list files", e);
            }

            // Calculate the digests of files and directories
            Map<String, DirectoryState> previousLevelDirectoryStates = Collections.unmodifiableMap(new HashMap<String, DirectoryState>());
            Map<String, DirectoryState> currentLevelDirectoryStates = new ConcurrentHashMap<String, DirectoryState>();
            for ( int levelIndex = lowestLevel ; levelIndex >= 0 ; levelIndex-- ) {
                final File directoryLevelListFile = stateFileUtil.getDirsListFile(levelIndex);
                threadPool = ThreadUtils.newFixedThreadPool(4);

                if ( directoryLevelListFile.exists() ) {
                    final File stateFile = stateFileUtil.getNewDirsStateFile(stateFileUtil.getDirsStateFilename(levelIndex));
                    final StateFileWriter newDirectoriesStateFileWriter = new StateFileWriter(ioFactory, stateFile);

                    BufferedReader directoryListFileReader = null;

                    try {
                        directoryListFileReader = new BufferedReader(new FileReader(directoryLevelListFile));

                        String absoluteDirectoryPath = null;
                        while ( (absoluteDirectoryPath = directoryListFileReader.readLine() ) != null ) {
                            final DirectoryState directoryState = directoryStateBuilder.
                                    directory(new File(absoluteDirectoryPath)).
                                    getDirectoryState();

                            final DirectoryStateDigestCalculator digestCalculator = new DirectoryStateDigestCalculator(directoryState, digesterCache, digesterUtil, this, currentLevelDirectoryStates, previousLevelDirectoryStates, ioFactory);

                            threadPool.submit(digestCalculator);
                        }

                        // each directory level has to be processed before continueing with the next(higher) level
                        ThreadUtils.shutdown(threadPool);

                        final List<DirectoryState> currentLevelDirectoryStatesList = new ArrayList<DirectoryState>(currentLevelDirectoryStates.values());

                        Collections.sort(currentLevelDirectoryStatesList);
                        // if one of the directory state processors failed -> change detection fails
                        // This is a simple not so efficient way, we could fail earlier but that will make everything more complicated
                        
                        for ( final DirectoryState directoryState : currentLevelDirectoryStatesList ) {
                            final Throwable failureCause = directoryState.getFailureCause();
                            if ( failureCause != null )
                                throw new GradleException("Failed to detect changes", failureCause);
                            else {
                                newDirectoriesStateFileWriter.addDigest(directoryState.getRelativePath(), directoryState.getDigest());
                            }
                        }

                    }
                    catch ( IOException e ) {
                        throw new GradleException("failed to detect changes (dirs."+newDirectoriesStateFileWriter.getStateFile().getAbsolutePath()+".state write failed)", e);
                    }
                    finally {
                        IOUtils.closeQuietly(directoryListFileReader);
                        FileUtils.deleteQuietly(directoryLevelListFile);
                        newDirectoriesStateFileWriter.close();
                    }
                }

                previousLevelDirectoryStates = Collections.unmodifiableMap(new HashMap<String, DirectoryState>(currentLevelDirectoryStates));
                currentLevelDirectoryStates = new ConcurrentHashMap<String, DirectoryState>();
            }

            // Compare new and old directory state + notify DirectoryStateChangeDetecterListener
            try {
                boolean keepComparing = true;
                int currentLevel = 0;

                final StateChangeEventDispatcher stateChangeEventDispatcher = new StateChangeEventDispatcher(stateChangeEventQueue, 100L, TimeUnit.MILLISECONDS, changeProcessor);
                final Thread changeProcessorEventThread = new Thread(stateChangeEventDispatcher);
                changeProcessorEventThread.start();
                
                threadPool = ThreadUtils.newFixedThreadPool(4);

                while ( keepComparing && currentLevel <= lowestLevel) {
                    keepComparing = stateComparator.compareState(this, currentLevel);

                    currentLevel++;
                }

                ThreadUtils.shutdown(threadPool);

                while ( !stateChangeEventQueue.isEmpty() ) {
                    Thread.yield();
                }
                stateChangeEventDispatcher.stopConsuming();

                ThreadUtils.join(changeProcessorEventThread);

                for (DirectoryStateDigestComparator directoryStateDigestComparator : directoryStateDigestComparators) {
                    final Throwable failureCause = directoryStateDigestComparator.getFailureCause();
                    if ( failureCause != null )
                        throw new GradleException("failed to compare directory state", failureCause);
                }
            }
            catch ( IOException e ) {
                throw new GradleException("failed to compare new and old state", e);
            }

            // Remove old directory state
            try {
                FileUtils.deleteDirectory(stateFileUtil.getOldDirectoryStateDir());
            }
            catch ( IOException e ) {
                throw new GradleException("failed to clean old state", e);
            }
            // Move new to old directory state
            try {
                FileUtils.moveDirectory(stateFileUtil.getNewDirectoryStateDir(), stateFileUtil.getOldDirectoryStateDir());
            }
            catch ( IOException e ) {
                throw new GradleException("failed to transfer current state to old state", e);
            }
        }
        finally {
            System.out.println(c.getTime());
        }
    }

    void submitDirectoryStateDigestComparator(final DirectoryStateDigestComparator directoryStateDigestComparator) {
        threadPool.submit(directoryStateDigestComparator);
        directoryStateDigestComparators.add(directoryStateDigestComparator);
    }

    BlockingQueueItemProducer<StateChangeEvent> getChangeProcessorEventProducer() {
        return changeProcessorEventProducer;
    }

    StateFileUtil getStateFileUtil() {
        return stateFileUtil;
    }

    public StateFileChangeListenerUtil getStateFileChangeListenerUtil() {
        return stateFileChangeListenerUtil;
    }
}
