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

package org.gradle.api.artifacts.indexing;

import org.gradle.api.GradleException;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.FileWriter;

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
