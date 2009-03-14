package org.gradle.util;

/**
 * @author Tom Eyckmans
 */
public interface InterruptHandler<T> {
    boolean handleIterrupt(T interrupted, InterruptedException interruptedException);
}
