package org.gradle.api.io;

import java.io.*;

/**
 * @author Tom Eyckmans
 */
public interface IoFactory {
    BufferedReader createBufferedReader(File file) throws IOException;
    BufferedWriter createBufferedWriter(File file) throws IOException;
    BufferedInputStream createBufferedInputStream(File file) throws IOException;
    BufferedOutputStream createBufferedOutputStream(File file) throws IOException;
}
