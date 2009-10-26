package org.gradle.api.file;

/**
 * The EmptyFileVisitor can be extends by implementations that only require to implement one of the 2 visit methods
 * (dir or file). This is just to limit the amount of code clutter when not both visit methods need to be implemented.
 *
 * @author Tom Eyckmans
 */
public class EmptyFileVisitor implements FileVisitor {

    public void visitDir(FileVisitDetails dirDetails) { }

    public void visitFile(FileVisitDetails fileDetails) { }
}
