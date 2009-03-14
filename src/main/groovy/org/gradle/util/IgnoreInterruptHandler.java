package org.gradle.util;

/**
 * @author Tom Eyckmans
 */
public class IgnoreInterruptHandler<T> implements InterruptHandler<T> {
    public boolean handleIterrupt(T interruptedThead, InterruptedException interruptedException) {
        return false;
    }
}
