package org.gradle.api.artifacts.indexing;

import org.gradle.api.GradleException;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.Enumeration;

/**
 * @author Tom Eyckmans
 */
public class JarArtifactIndexer {
    private IndexFileUtil indexFileUtil;

    public JarArtifactIndexer(IndexFileUtil indexFileUtil) {
        this.indexFileUtil = indexFileUtil;
    }

    public File index(File jarFile) {
        if ( jarFile == null ) throw new IllegalArgumentException("jarFile is null!");

        final String jarFileAbsolutePath = jarFile.getAbsolutePath();

        if ( !jarFile.exists() ) throw new IllegalArgumentException("jarFile doesn't exists! (" + jarFileAbsolutePath + ")");
        if ( !jarFile.isFile() ) throw new IllegalArgumentException("jarFile is not a file! (" + jarFileAbsolutePath + ")");
        if ( !jarFile.getName().endsWith(".jar") ) throw new IllegalArgumentException("jarFile is not a jarFile! (" + jarFileAbsolutePath + ")");

        final File packageIndexFile = indexFileUtil.packageIndexFile(jarFile);


        BufferedWriter indexFileWriter = null;
        try {
            indexFileWriter = new BufferedWriter(new FileWriter(packageIndexFile));

            final BufferedWriter writer = indexFileWriter;
            
            new JarFilePackageLister().listJarPackages(
                    jarFile,
                    new JarFilePackageListener() {
                        public void receivePackage(String packageName) {
                            try {
                                writer.write(packageName);
                                writer.newLine();
                            }
                            catch ( IOException e ) {
                                throw new GradleException("failed to write to index file", e);
                            }
                        }
                    }
            );

            indexFileWriter.flush();

        } catch (IOException e) {
            throw new GradleException("failed to index jar file (" + jarFileAbsolutePath + ")", e);
        }
        finally {
            IOUtils.closeQuietly(indexFileWriter);
        }

        return packageIndexFile;
    }
}
