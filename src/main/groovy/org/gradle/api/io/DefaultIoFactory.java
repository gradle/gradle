package org.gradle.api.io;

import java.io.*;

/**
 * @author Tom Eyckmans
 */
public class DefaultIoFactory implements IoFactory {
    public BufferedReader createBufferedReader(File file) throws IOException {
        return new BufferedReader(new FileReader(file));
    }

    public BufferedWriter createBufferedWriter(File file) throws IOException {
        return new BufferedWriter(new FileWriter(file));
    }

    public BufferedInputStream createBufferedInputStream(File file) throws IOException {
        return new BufferedInputStream(new FileInputStream(file));
    }

    public BufferedOutputStream createBufferedOutputStream(File file) throws IOException {
        return new BufferedOutputStream(new FileOutputStream(file));
    }
}
