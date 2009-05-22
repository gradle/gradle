package org.gradle.api.artifacts.indexing;

import org.gradle.api.GradleException;

import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.Enumeration;
import java.io.File;
import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class JarFilePackageLister {
    public void listJarPackages(File jarFile, JarFilePackageListener listener) {
        if ( jarFile == null ) throw new IllegalArgumentException("jarFile is null!");

        final String jarFileAbsolutePath = jarFile.getAbsolutePath();

        if ( !jarFile.exists() ) throw new IllegalArgumentException("jarFile doesn't exists! (" + jarFileAbsolutePath + ")");
        if ( !jarFile.isFile() ) throw new IllegalArgumentException("jarFile is not a file! (" + jarFileAbsolutePath + ")");
        if ( !jarFile.getName().endsWith(".jar") ) throw new IllegalArgumentException("jarFile is not a jarFile! (" + jarFileAbsolutePath + ")");

        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(jarFile);

            final Enumeration<? extends ZipEntry> zipFileEntries = zipFile.entries();

            while ( zipFileEntries.hasMoreElements() ) {
                final ZipEntry zipFileEntry = zipFileEntries.nextElement();

                if ( zipFileEntry.isDirectory() ) {
                    final String zipFileEntryName = zipFileEntry.getName();

                    if ( !zipFileEntryName.startsWith("META-INF") )
                        listener.receivePackage(zipFileEntryName);
                }
            }
        }
        catch (IOException e) {
            throw new GradleException("failed to scan jar file for packages (" + jarFileAbsolutePath + ")", e);
        }
        finally {
            if ( zipFile != null ) {
                try {
                    zipFile.close();
                }
                catch ( IOException e ) {
                    // ignore
                }
            }
        }
    }
}