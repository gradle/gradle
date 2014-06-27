/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.gradleplugin.userinterface.swing.standalone;

import org.gradle.internal.UncheckedException;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;

/**
 * This is the same as Application, but this version blocks the calling thread until the Application shuts down.
 */
public class BlockingApplication {

    /**
     * This launches this application and blocks until it closes. Useful for being called from the gradle command line. We launch this in the Event Dispatch Thread and block the calling thread.
     */
    public static void launchAndBlock() {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new RuntimeException("Cannot launch and block from the Event Dispatch Thread!");
        }

        //create a lock to wait on
        final CountDownLatch completed = new CountDownLatch(1);

        //instantiate the app in the Event Dispatch Thread
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    new Application(new Application.LifecycleListener() {
                        public void hasShutDown() {
                            completed.countDown();
                        }
                    });
                }
            });

            completed.await();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (InvocationTargetException e) {
            throw UncheckedException.unwrapAndRethrow(e);
        }
    }
}
