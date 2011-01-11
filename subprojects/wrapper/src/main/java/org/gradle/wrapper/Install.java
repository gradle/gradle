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

package org.gradle.wrapper;

import java.io.*;
import java.net.URI;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Hans Dockter
 */
public class Install {
    private final IDownload download;
    private final boolean alwaysDownload;
    private final boolean alwaysUnpack;
    private final PathAssembler pathAssembler;

    public Install(boolean alwaysDownload, boolean alwaysUnpack, IDownload download, PathAssembler pathAssembler) {
        this.alwaysDownload = alwaysDownload;
        this.alwaysUnpack = alwaysUnpack;
        this.download = download;
        this.pathAssembler = pathAssembler;
    }

    public File createDist(URI distributionUrl, String distBase, String distPath, String zipBase, String zipPath) throws Exception {
        File gradleHome = pathAssembler.gradleHome(distBase, distPath, distributionUrl);
        if (!alwaysDownload && !alwaysUnpack && gradleHome.isDirectory()) {
            return gradleHome;
        }
        File localZipFile = pathAssembler.distZip(zipBase, zipPath, distributionUrl);
        if (alwaysDownload || !localZipFile.exists()) {
            File tmpZipFile = new File(localZipFile.getParentFile(), localZipFile.getName() + ".part");
            tmpZipFile.delete();
            System.out.println("Downloading " + distributionUrl);
            download.download(distributionUrl, tmpZipFile);
            tmpZipFile.renameTo(localZipFile);
        }
        if (gradleHome.isDirectory()) {
            System.out.println("Deleting directory " + gradleHome.getAbsolutePath());
            deleteDir(gradleHome);
        }
        File distDest = gradleHome.getParentFile();
        System.out.println("Unzipping " + localZipFile.getAbsolutePath() + " to " + distDest.getAbsolutePath());
        unzip(localZipFile, distDest);
        if (!gradleHome.isDirectory()) {
            throw new RuntimeException(String.format(
                    "Gradle distribution '%s' does not contain expected directory '%s'.", distributionUrl,
                    gradleHome.getName()));
        }
        setExecutablePermissions(gradleHome);
        return gradleHome;
    }

    private void setExecutablePermissions(File gradleHome) {
        if (isWindows()) {
            return;
        }
        File gradleCommand = new File(gradleHome, "bin/gradle");
        String errorMessage = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("chmod", "755", gradleCommand.getCanonicalPath());
            Process p = pb.start();
            if (p.waitFor() == 0) {
                System.out.println("Set executable permissions for: " + gradleCommand.getAbsolutePath());
            } else {
                BufferedReader is = new BufferedReader(new InputStreamReader(p.getInputStream()));
                errorMessage = "";
                String line;
                while ((line = is.readLine()) != null) {
                    errorMessage += line + System.getProperty("line.separator");
                }
            }
        } catch (IOException e) {
            errorMessage = e.getMessage();
        } catch (InterruptedException e) {
            errorMessage = e.getMessage();
        }
        if (errorMessage != null) {
            System.out.println("Could not set executable permissions for: " + gradleCommand.getAbsolutePath());
            System.out.println("Please do this manually if you want to use the Gradle UI.");
        }
    }

    private boolean isWindows() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.US);
        if (osName.indexOf("windows") > -1) {
            return true;
        }
        return false;
    }

    private boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }

        // The directory is now empty so delete it
        return dir.delete();
    }

    public void unzip(File zip, File dest) throws IOException {
        Enumeration entries;
        ZipFile zipFile;

        zipFile = new ZipFile(zip);

        entries = zipFile.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) entries.nextElement();

            if (entry.isDirectory()) {
                (new File(dest, entry.getName())).mkdirs();
                continue;
            }

            copyInputStream(zipFile.getInputStream(entry),
                    new BufferedOutputStream(new FileOutputStream(new File(dest, entry.getName()))));
        }
        zipFile.close();
    }

    public void copyInputStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int len;

        while ((len = in.read(buffer)) >= 0) {
            out.write(buffer, 0, len);
        }

        in.close();
        out.close();
    }

}
