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

import org.gradle.internal.file.locking.ExclusiveFileAccessManager;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static org.gradle.internal.file.PathTraversalChecker.safePathName;
import static org.gradle.wrapper.Download.safeUri;

public class Install {
    public static final String DEFAULT_DISTRIBUTION_PATH = "wrapper/dists";
    public static final String SHA_256 = ".sha256";
    public static final int RETRIES = 3;
    private final Logger logger;
    private final IDownload download;
    private final PathAssembler pathAssembler;
    private final ExclusiveFileAccessManager exclusiveFileAccessManager = new ExclusiveFileAccessManager(120000, 200);

    public Install(Logger logger, IDownload download, PathAssembler pathAssembler) {
        this.logger = logger;
        this.download = download;
        this.pathAssembler = pathAssembler;
    }

    public File createDist(final WrapperConfiguration configuration) throws Exception {
        final URI distributionUrl = configuration.getDistribution();

        final PathAssembler.LocalDistribution localDistribution = pathAssembler.getDistribution(configuration);
        final File distDir = localDistribution.getDistributionDir();
        final File localZipFile = localDistribution.getZipFile();

        return exclusiveFileAccessManager.access(localZipFile, new Callable<File>() {
            @Override
            public File call() throws Exception {
                final File markerFile = new File(localZipFile.getParentFile(), localZipFile.getName() + ".ok");
                if (distDir.isDirectory() && markerFile.isFile()) {
                    InstallCheck installCheck = verifyDistributionRoot(distDir, distDir.getAbsolutePath());
                    if (installCheck.isVerified()) {
                        return installCheck.gradleHome;
                    }
                    // Distribution is invalid. Try to reinstall.
                    System.err.println(installCheck.failureMessage);
                    markerFile.delete();
                }

                fetchDistribution(localZipFile, distributionUrl, distDir, configuration);

                InstallCheck installCheck = verifyDistributionRoot(distDir, safeUri(distributionUrl).toASCIIString());
                if (installCheck.isVerified()) {
                    setExecutablePermissions(installCheck.gradleHome);
                    markerFile.createNewFile();
                    localZipFile.delete();
                    return installCheck.gradleHome;
                }
                // Distribution couldn't be installed.
                throw new RuntimeException(installCheck.failureMessage);
            }
        });
    }

    private void fetchDistribution(File localZipFile, URI distributionUrl, File distDir, WrapperConfiguration configuration) throws Exception {
        String distributionSha256Sum = configuration.getDistributionSha256Sum();
        boolean failed = false;
        int retries = RETRIES;
        do {
            try {
                boolean needsDownload = !localZipFile.isFile() || failed;
                if (needsDownload) {
                    forceFetch(localZipFile, distributionUrl);
                }

                deleteLocalTopLevelDirs(distDir);

                verifyDownloadChecksum(configuration.getDistribution().toASCIIString(), localZipFile, distributionSha256Sum);

                unzipLocal(localZipFile, distDir);
                failed = false;
            } catch (ZipException e) {
                if (retries >= RETRIES && distributionSha256Sum == null) {
                    distributionSha256Sum = fetchDistributionSha256Sum(configuration, localZipFile);
                }
                failed = true;
                retries--;
                if(retries <= 0){
                    throw new RuntimeException("Downloaded distribution file " + localZipFile + " is no valid zip file.");
                }
            }
        } while (failed);
    }


    private String fetchDistributionSha256Sum(WrapperConfiguration configuration, File localZipFile) {
        URI distribution = configuration.getDistribution();
        try {
            URI distributionUrl = distribution.resolve(distribution.getPath() + SHA_256);
            File tmpZipFile = new File(localZipFile.getParentFile(), localZipFile.getName() + SHA_256);

            forceFetch(tmpZipFile, distributionUrl);

            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(tmpZipFile), "UTF-8"));
            try {
                return reader.readLine();
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            logger.log("Could not fetch hash for " + safeUri(distribution) + ".");
            logger.log("Reason: " + e.getMessage());
            return null;
        }
    }

    private void unzipLocal(File localZipFile, File distDir) throws IOException {
        try {
            unzip(localZipFile, distDir);
        } catch (IOException e) {
            logger.log("Could not unzip " + localZipFile.getAbsolutePath() + " to " + distDir.getAbsolutePath() + ".");
            logger.log("Reason: " + e.getMessage());
            throw e;
        }
    }

    private void deleteLocalTopLevelDirs(final File distDir) {
        List<File> topLevelDirs = listDirs(distDir);
        for (File dir : topLevelDirs) {
            logger.log("Deleting directory " + dir.getAbsolutePath());
            deleteDir(dir);
        }
    }

    private void forceFetch(File localTargetFile, URI distributionUrl) throws Exception {
        File tempDownloadFile = new File(localTargetFile.getParentFile(), localTargetFile.getName() + ".part");
        tempDownloadFile.delete();

        logger.log("Downloading " + safeUri(distributionUrl));
        download.download(distributionUrl, tempDownloadFile);
        if(localTargetFile.exists()) {
            localTargetFile.delete();
        }
        tempDownloadFile.renameTo(localTargetFile);
    }

    static String calculateSha256Sum(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        InputStream fis = new FileInputStream(file);
        try {
            int n = 0;
            byte[] buffer = new byte[4096];
            while (n != -1) {
                n = fis.read(buffer);
                if (n > 0) {
                    md.update(buffer, 0, n);
                }
            }
        } finally {
            fis.close();
        }

        byte[] byteData = md.digest();
        StringBuilder hexString = new StringBuilder();
        for (int i = 0; i < byteData.length; i++) {
            String hex = Integer.toHexString(0xff & byteData[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }

        return hexString.toString();
    }

    private InstallCheck verifyDistributionRoot(File distDir, String distributionDescription) {
        List<File> dirs = listDirs(distDir);
        if (dirs.isEmpty()) {
            return InstallCheck.failure(format("Gradle distribution '%s' does not contain any directories. Expected to find exactly 1 directory.", distributionDescription));
        }
        if (dirs.size() != 1) {
            return InstallCheck.failure(format("Gradle distribution '%s' contains too many directories. Expected to find exactly 1 directory.", distributionDescription));
        }

        File gradleHome = dirs.get(0);
        if (BootstrapMainStarter.findLauncherJar(gradleHome) == null) {
            return InstallCheck.failure(format("Gradle distribution '%s' does not appear to contain a Gradle distribution.", distributionDescription));
        }
        return InstallCheck.success(gradleHome);
    }

    private void verifyDownloadChecksum(String sourceUrl, File localZipFile, String expectedSum) throws Exception {
        if (expectedSum == null) {
            return;
        }
        // if a SHA-256 hash sum has been defined in gradle-wrapper.properties, verify it here
        String actualSum = calculateSha256Sum(localZipFile);
        if (expectedSum.equals(actualSum)) {
            return;
        }

        localZipFile.delete();
        String message = format("Verification of Gradle distribution failed!%n" +
                "%n" +
                "Your Gradle distribution may have been tampered with.%n" +
                "Confirm that the 'distributionSha256Sum' property in your gradle-wrapper.properties file is correct and you are downloading the wrapper from a trusted source.%n" +
                "%n" +
                "Distribution Url: %s%n" +
                "Download Location: %s%n" +
                "Expected checksum: '%s'%n" +
                "Actual checksum:   '%s'%n" +
                "Visit https://gradle.org/release-checksums/ to verify the checksums of official distributions. If your build uses a custom distribution, see with its provider.",
            sourceUrl, localZipFile.getAbsolutePath(), expectedSum, actualSum);
        throw new RuntimeException(message);
    }

    @SuppressWarnings("MixedMutabilityReturnType")
    private List<File> listDirs(File distDir) {
        if (!distDir.exists()) {
            return emptyList();
        }
        File[] files = distDir.listFiles();
        if (files == null) {
            return emptyList();
        }

        List<File> dirs = new ArrayList<File>();
        for (File file : files) {
            if (file.isDirectory()) {
                dirs.add(file);
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
            if (p.waitFor() != 0) {
                BufferedReader is = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
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
            Thread.currentThread().interrupt();
            errorMessage = e.getMessage();
        }
        if (errorMessage != null) {
            logger.log("Could not set executable permissions for: " + gradleCommand.getAbsolutePath());
        }
    }

    private boolean isWindows() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.US);
        return osName.contains("windows");
    }

    private boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    boolean success = deleteDir(new File(dir, children[i]));
                    if (!success) {
                        return false;
                    }
                }
            }
        }

        // The directory is now empty so delete it
        return dir.delete();
    }

    private void unzip(File zip, File dest) throws IOException {
        ZipFile zipFile = new ZipFile(zip);
        try {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                File destFile = new File(dest, safePathName(entry.getName()));
                if (entry.isDirectory()) {
                    destFile.mkdirs();
                    continue;
                }

                OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(destFile));
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

    private static class InstallCheck {
        private final File gradleHome;
        private final String failureMessage;

        private static InstallCheck failure(String message) {
            return new InstallCheck(null, message);
        }

        private static InstallCheck success(File gradleHome) {
            return new InstallCheck(gradleHome, null);
        }

        private InstallCheck(File gradleHome, String failureMessage) {
            this.gradleHome = gradleHome;
            this.failureMessage = failureMessage;
        }

        private boolean isVerified() {
            return gradleHome != null;
        }
    }

}
