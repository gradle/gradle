/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.gradleplugin.userinterface.swing.common;

import javax.swing.SwingUtilities;

//this was a public version of SwingWorker developed by Sun prior to Java 1.6's release. This is used to allow compatiblity with Java 1.5.

/**
 * This is the 3rd version of SwingWorker (also known as SwingWorker 3), an abstract class that you subclass to perform
 * GUI-related work in a dedicated thread.  For instructions on using this class, see: <p/>
 * http://java.sun.com/docs/books/tutorial/uiswing/misc/threads.html <p/> Note that the API changed slightly in the 3rd
 * version: You must now invoke start() on the SwingWorker after creating it.
 */
public abstract class SwingWorker {
    public Object myObject = null;
    private Object value;  // see getValue(), setValue()

    /**
     * Class to maintain reference to current worker thread under separate synchronization control.
     */
    private static class ThreadVar {
        private Thread thread;

        ThreadVar(Thread t) {
            thread = t;
        }

        synchronized Thread get() {
            return thread;
        }

        synchronized void clear() {
            thread = null;
        }
    }

    private ThreadVar threadVar;

    /**
     * Get the value produced by the worker thread, or null if it hasn't been constructed yet.
     */
    protected synchronized Object getValue() {
        return value;
    }

    /**
     * Set the value produced by worker thread
     */
    private synchronized void setValue(Object x) {
        value = x;
    }

    /**
     * Compute the value to be returned by the <code>get</code> method.
     */
    public abstract Object construct();

    /**
     * Called on the event dispatching thread (not on the worker thread) after the <code>construct</code> method has
     * returned.
     */
    public void finished() {
    }

    /**
     * A new method that interrupts the worker thread.  Call this method to force the worker to stop what it's doing.
     */
    public void interrupt() {
        Thread t = threadVar.get();
        if (t != null) {
            t.interrupt();
        }
        threadVar.clear();
    }

    /**
     * Return the value created by the <code>construct</code> method. Returns null if either the constructing thread or
     * the current thread was interrupted before a value was produced.
     *
     * @return the value created by the <code>construct</code> method
     */
    public Object get() {
        while (true) {
            Thread t = threadVar.get();
            if (t == null) {
                return getValue();
            }
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // propagate
                return null;
            }
        }
    }

    public SwingWorker() //this is just here for convienence.
    {
        constructor(null, null);
    }

    /**
     * Start a thread that will call the <code>construct</code> method and then exit.
     */
    public SwingWorker(Object obj) {
        constructor(obj, null);
    }

    public SwingWorker(String name) {
        constructor(null, name);
    }

    public SwingWorker(Object obj, String name) {
        constructor(obj, name);
    }

    private void constructor(Object obj, String name) {
        myObject = obj;

        final Runnable doFinished = new Runnable() {
            public void run() {
                finished();
            }
        };

        Runnable doConstruct = new Runnable() {
            public void run() {
                try {
                    setValue(construct());
                } finally {
                    threadVar.clear();
                }

                SwingUtilities.invokeLater(doFinished);
            }
        };

        String threadName = "Swing Worker";
        if (name != null) {
            threadName += " -- " + name;
        }

        Thread t = new Thread(doConstruct, threadName);
        threadVar = new ThreadVar(t);
    }

    /**
     * Start the worker thread.
     */
    public void start() {
        start(Thread.NORM_PRIORITY);
    }

    public void start(int priority) {
        Thread t = threadVar.get();

        if (t != null) {
            // set the thread's priority
            try {
                t.setPriority(priority);
            } catch (SecurityException e) {
            }

            t.start();
        }
    }
}
