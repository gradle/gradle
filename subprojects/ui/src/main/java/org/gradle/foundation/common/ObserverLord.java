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
package org.gradle.foundation.common;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This is a Swing-friendly observer manager class. Swing-friendly, but can be used by non-Swing classes. Its meant to abstract the fact that you probably need to be in the Event Dispatch Thread when
 * receiving notifications inside Swing-related classes.
 *
 * To use this class, add it as a member variable (don't derive from this!) of a class that you want to be observered. You can have multiple instances of this if you want to allow for a finer
 * granularity of observing (similar to components having mouse move listeners and mouse (click) listeners). Next, create an interface for the observers. Now implement add and remove observer
 * functions that call the add and remove functions here. Lastly, implement ObserverNotification and have it call the aforementioned observer interface appropriately. Note: you should actually
 * implement ObserverNotification for each "message" you want to send. Example: One that would tell a view a node was added. One that would tell a view a node was deleted, etc. While you have multiple
 * notification classes, you only need 1 (or few) actual observer interfaces, containing all the possible functions called by all notifications.
 */
public class ObserverLord<E> {
    private List<E> regularObservers = new ArrayList<E>();
    private List<E> eventQueueObservers = new ArrayList<E>();

    private final Logger logger = Logging.getLogger(ObserverLord.class);

    /**
     * Implement this for each call to ObserverLord.notifyObservers. The notify function usually just has a single call to a function on the observer.
     *
     * Example:
     * <pre>
     * public void notify( MyObserver observer )
     * {
     * observer.myfunction();
     * }
     * </pre>
     */
    public interface ObserverNotification<E> {
        public void notify(E observer);
    }

    /**
     * Adds an observer to our messaging system.
     *
     * <!       Name        Description  >
     *
     * @param observer observer to add.
     * @param inEventQueue true to notify this observer only in the event queue, false to notify it immediately.
     */

    public void addObserver(E observer, boolean inEventQueue) {
        if (!inEventQueue) {
            addIfNew(observer, regularObservers);
        } else {
            addIfNew(observer, eventQueueObservers);
        }
    }

    private void addIfNew(E observer, List<E> destinationList) {
        if (!destinationList.contains(observer)) {
            destinationList.add(observer);
        }
    }

    /**
     * Deletes an observer in our messaging system.
     *
     * <!       Name     Dir   Description  >
     *
     * @param observer in,
     */
    public void removeObserver(E observer) {
        regularObservers.remove(observer);
        eventQueueObservers.remove(observer);
    }

    public void removeAllObservers() {
        regularObservers.clear();
        eventQueueObservers.clear();
    }

    /**
     * Messaging method that handles telling each observer that something happen to the observable.
     *
     * <!       Name        Dir   Description  >
     *
     * @param notification in,  notification sent to the observer
     */
    public void notifyObservers(ObserverNotification<E> notification) {
        //notify all the non-event queue observers now.
        notifyObserversInternal(regularObservers, notification);
        notifyObserversInEventQueueThread(notification);
    }

    /**
     * Here is where we notify all the event queue observers. To notify the event queue observers we have to make sure it occurs in the event queue thread. If we're not in the event queue, we'll wrap
     * it in an invoke and wait.
     *
     * <!       Name        Dir   Description  > <!       Name        Dir   Description  >
     *
     * @param notification in,  notification sent to the observer
     */
    private void notifyObserversInEventQueueThread(final ObserverNotification<E> notification) {
        if (eventQueueObservers.size() == 0) //if we have no event queue observsers, we're done
        {
            return;
        }

        if (EventQueue.isDispatchThread()) {
            notifyObserversInternal(eventQueueObservers, notification);
        } else {
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    public void run() {
                        notifyObserversInternal(eventQueueObservers, notification);
                    }
                });
            } catch (Exception e) {
                logger.error("notifyObservers exception", e);
            }
        }
    }

    /**
     * The internal mechanism that actually notifies the observers. We just iterate though each observer and pass it to the notification mechanism.
     *
     *
     * <!       Name         Dir  Description  >
     *
     * @param observers in,  objects that changed (observable)
     * @param notification in,  notification sent to the observer
     */
    private void notifyObserversInternal(List<E> observers, ObserverNotification<E> notification) {
        Iterator<E> iterator = observers.iterator();
        while (iterator.hasNext()) {
            E observer = iterator.next();
            try {
                notification.notify(observer);
            } catch (Exception e) //this is so an error in the notification doesn't stop the entire process.
            {
                logger.error("error notifying observers", e);
            }
        }
    }

    public String toString() {
        return regularObservers.size() + " regular observers, " + eventQueueObservers.size() + " event queue observers";
    }
}