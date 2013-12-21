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
import java.util.*;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Install {
    public static final String DEFAULT_DISTRIBUTION_PATH = "wrapper/dists";
    private final IDownload download;
    private final PathAssembler pathAssembler;
    private final ExclusiveFileAccessManager exclusiveFileAccessManager = new ExclusiveFileAccessManager(120000, 200);

    public Install(IDownload download, PathAssembler pathAssembler) {
        this.download = download;
        this.pathAssembler = pathAssembler;
    }

    public File createDist(WrapperConfiguration configuration) throws Exception {
        final URI distributionUrl = configuration.getDistribution();

        final PathAssembler.LocalDistribution localDistribution = pathAssembler.getDistribution(configuration);
        final File distDir = localDistribution.getDistributionDir();
        final File localZipFile = localDistribution.getZipFile();

        return exclusiveFileAccessManager.access(localZipFile, new Callable<File>() {
            public File call() throws Exception {
                final File markerFile = new File(localZipFile.getParentFile(), localZipFile.getName() + ".ok");
                if (distDir.isDirectory() && markerFile.isFile()) {
                    return getDistributionRoot(distDir, distDir.getAbsolutePath());
                }

                boolean needsDownload = !localZipFile.isFile();

                if (needsDownload) {
                    File tmpZipFile = new File(localZipFile.getParentFile(), localZipFile.getName() + ".part");
                    tmpZipFile.delete();
                    System.out.println("Downloading " + distributionUrl);
                    download.download(distributionUrl, tmpZipFile);
                    tmpZipFile.renameTo(localZipFile);
                }

                List<File> topLevelDirs = listDirs(distDir);
                for (File dir : topLevelDirs) {
                    System.out.println("Deleting directory " + dir.getAbsolutePath());
                    deleteDir(dir);
                }
                System.out.println("Unzipping " + localZipFile.getAbsolutePath() + " to " + distDir.getAbsolutePath());
                unzip(localZipFile, distDir);

                File root = getDistributionRoot(distDir, distributionUrl.toString());
                setExecutablePermissions(root);
                markerFile.createNewFile();

                return root;
            }
        });
    }

    private File getDistributionRoot(File distDir, String distributionDescription) {
        List<File> dirs = listDirs(distDir);
        if (dirs.isEmpty()) {
            throw new RuntimeException(String.format("Gradle distribution '%s' does not contain any directories. Expected to find exactly 1 directory.", distributionDescription));
        }
        if (dirs.size() != 1) {
            throw new RuntimeException(String.format("Gradle distribution '%s' contains too many directories. Expected to find exactly 1 directory.", distributionDescription));
        }
        return dirs.get(0);
    }

    private List<File> listDirs(File distDir) {
        List<File> dirs = new ArrayList<File>();
        if (distDir.exists()) {
            for (File file : distDir.listFiles()) {
                if (file.isDirectory()) {
                    dirs.add(file);
                }
            }
        }
        return dirs;
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
                Formatter stdout = new Formatter();
                String line;
                while ((line = is.readLine()) != null) {
                    stdout.format("%s%n", line);
                }
                errorMessage = stdout.toString();
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

    private void unzip(File zip, File dest) throws IOException {
        Enumeration entries;
        ZipFile zipFile = new ZipFile(zip);

        try {
            entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) entries.nextElement();

                if (entry.isDirectory()) {
                    (new File(dest, entry.getName())).mkdirs();
                    continue;
                }

                OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(new File(dest, entry.getName())));
                try {
                    copyInputStream(zipFile.getInputStream(entry), outputStream);
                } finally {
                    outputStream.close();
                }
            }
        } finally {
            zipFile.close();
        }
    }

    private void copyInputStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int len;

        while ((len = in.read(buffer)) >= 0) {
            out.write(buffer, 0, len);
        }

        in.close();
        out.close();
    }


}
