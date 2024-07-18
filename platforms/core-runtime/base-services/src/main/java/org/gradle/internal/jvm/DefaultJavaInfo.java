/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.jvm;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.jvm.JavaVersionParser;
import org.gradle.internal.os.OperatingSystem;

import javax.annotation.Nullable;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of {@link JavaInfo}.
 */
@NonNullApi
public class DefaultJavaInfo implements JavaInfo {

    /**
     * The JAVA_HOME path represented by this JavaInfo.
     */
    private final File javaHome;

    /**
     * The operating system this JAVA_HOME is located on.
     */
    private final OperatingSystem os;

    // Cached resolved state
    private File javaExecutable;
    private File javacExecutable;
    private File javadocExecutable;
    private File toolsJar;
    private Boolean jdk;

    /**
     * Create a JavaInfo instance for the given JAVA_HOME path on the given operating system.
     * <p>
     * Before creating an instance, the JAVA_HOME should be normalized to escape from any
     * JRE installations to the owning JDK installation. Furthermore, JAVA_HOME must exist
     * and must contain at least a {@code java} executable.
     */
    protected DefaultJavaInfo(File javaHome, OperatingSystem os) {
        this.javaHome = javaHome;
        this.os = os;
    }

    /**
     * Used for unit testing when testing behavior of another OS.
     *
     * @see DefaultJavaInfo#forHome(File).
     */
    @Nullable
    @VisibleForTesting
    static JavaInfo forHome(File potentialJavaHome, OperatingSystem os) {
        File javaHome = normalizeJavaHome(potentialJavaHome, os);

        // We could not discover a valid JAVA_HOME at this location
        if (javaHome == null) {
            return null;
        }

        return new DefaultJavaInfo(javaHome, os);
    }

    /**
     * Create a JavaInfo instance for the given JAVA_HOME path.
     *
     * @return null if the given path is not a valid JAVA_HOME
     */
    @Nullable
    public static JavaInfo forHome(File potentialJavaHome) {
        return forHome(potentialJavaHome, OperatingSystem.current());
    }

    /**
     * Create a JavaInfo instance for a given executable within a JAVA_HOME.
     *
     * @return null if the given executable is not within a valid JAVA_HOME
     */
    @Nullable
    public static JavaInfo forExecutable(File javaExecutable) {
        File javaHome = javaExecutable.getParentFile().getParentFile();
        return forHome(javaHome);
    }

    /**
     * Get the operating system this JAVA_HOME is located on.
     */
    protected OperatingSystem getOs() {
        return os;
    }

    @Override
    public final File getJavaHome() {
        return javaHome;
    }

    @Override
    public File getJavaExecutable() {
        if (javaExecutable != null) {
            return javaExecutable;
        }
        javaExecutable = getExecutable("java");
        return javaExecutable;
    }

    @Override
    public File getJavacExecutable() {
        if (javacExecutable != null) {
            return javacExecutable;
        }
        javacExecutable = getExecutable("javac");
        return javacExecutable;
    }

    @Override
    public File getJavadocExecutable() {
        if (javadocExecutable != null) {
            return javadocExecutable;
        }
        javadocExecutable = getExecutable("javadoc");
        return javadocExecutable;
    }

    @Override
    public File getExecutable(String name) {
        File executable = commandLocation(name);
        if (executable.isFile()) {
            return executable;
        }

        return getFallbackExecutable(name, executable);
    }

    /**
     * If an executable cannot be found in the JAVA_HOME, handle
     * where to discover the executable.
     *
     * TODO: We should likely always throw an exception here. For backwards
     * compatibility reasons, we look elsewhere when modeling the current
     * JVM. However, we should treat the current JVM the same as any other.
     */
    protected File getFallbackExecutable(String command, File attempted) {
        throw new IllegalStateException(String.format(
            "Cannot find executable '%s' at location '%s'",
            command,
            attempted.getPath()
        ));
    }

    @Override
    public boolean isJdk() {
        if (jdk == null) {
            File executable = commandLocation("javac");
            jdk = executable.isFile();
        }
        return jdk;
    }

    @Override
    public File getToolsJar() {
        if (toolsJar != null) {
            return toolsJar;
        }

        File potentialToolsJar = new File(javaHome, "lib/tools.jar");
        if (potentialToolsJar.isFile()) {
            toolsJar = potentialToolsJar;
        }

        return toolsJar;
    }

    /**
     * Get the location of a command relative to the JAVA_HOME. The
     * returned file may not exist.
     */
    private File commandLocation(String command) {
        File exec = new File(javaHome, "bin/" + command);
        return new File(os.getExecutableName(exec.getAbsolutePath()));
    }

    @Override
    @Nullable
    public File getJre() {
        File embeddedJre = getEmbeddedJre();
        if (embeddedJre != null) {
            return embeddedJre;
        }
        return getStandaloneJre();
    }

    @Override
    @Nullable
    public File getEmbeddedJre() {
        File jreDir = new File(javaHome, "jre");
        if (jreDir.isDirectory()) {
            return jreDir;
        }
        return null;
    }

    @Nullable
    @Override
    public File getStandaloneJre() {
        return getStandaloneJre(javaHome, os);
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JavaInfo)) {
            return false;
        }

        JavaInfo that = (JavaInfo) o;
        return getJavaHome().equals(that.getJavaHome());
    }

    @Override
    public int hashCode() {
        return javaHome.hashCode();
    }

    @Override
    public String toString() {
        return "JAVA_HOME: " + javaHome;
    }

    /**
     * Normalize a potential JAVA_HOME by attempting to escape out of a JRE installation
     * to discover owning JDK installation.
     *
     * @return null if no valid JVM installation found.
     */
    @Nullable
    private static File normalizeJavaHome(File potentialJavaHome, OperatingSystem os) {
        String javaPath = os.getExecutableName("bin/java");

        // Attempt to escape out of a JRE installation by looking for the tools.jar
        // file, which is located in the owning JDK installation.
        // This only works for Java <= 8
        File toolsJar = findToolsJar(potentialJavaHome, os);
        if (toolsJar != null) {
            File jdkHome = toolsJar.getParentFile().getParentFile();
            if (new File(jdkHome, javaPath).isFile()) {
                return jdkHome;
            }
        }

        // Attempt to escape out of an embedded JRE installation for Java >= 9, where
        // tools.jar is no longer present.
        if (potentialJavaHome.getName().equalsIgnoreCase("jre")) {
            File jdkHome = potentialJavaHome.getParentFile();
            if (new File(jdkHome, javaPath).isFile()) {
                return potentialJavaHome.getParentFile();
            }
        }

        // Verify that our JAVA_HOME has at least a java executable.
        if (new File(potentialJavaHome, javaPath).isFile()) {
            return potentialJavaHome;
        }

        return null;
    }

    /**
     * Discover the tools.jar file for a given JAVA_HOME. This JAVA_HOME
     * may represent either a JRE or a JDK installation. Since the tools.jar
     * is only present in a JDK installation, this method will search parent
     * directories or parallel directories for a corresponding JDK that does
     * contain the tools.jar.
     * <p>
     * This method attempts to normalize a JAVA_HOME path so that it always
     * points to a JDK installation.
     * <p>
     * JVM installations newer than Java 8 do not contain a tools.jar file. In this
     * case, this method will return null.
     */
    @Nullable
    private static File findToolsJar(File javaHome, OperatingSystem os) {

        // Look for the file in the current directory
        File file = new File(javaHome, "lib/tools.jar");
        if (file.isFile()) {
            return file;
        }

        // If we are in an embedded JRE, check the parent directory
        if (javaHome.getName().equalsIgnoreCase("jre")) {
            File jdkHome = javaHome.getParentFile();
            file = new File(jdkHome, "lib/tools.jar");
            if (file.isFile()) {
                return file;
            }
        }

        // If there is a parallel JDK installation, there
        File jdkHome = getStandaloneJdk(javaHome, os);
        if (jdkHome != null) {
            file = new File(jdkHome, "lib/tools.jar");
            if (file.isFile()) {
                return file;
            }
        }

        return null;
    }

    /**
     * Matches a JRE directory followed by the java version.
     */
    private static final Pattern WINDOWS_JDK_DIR_PATTERN = Pattern.compile("jdk([\\d._]+)");

    /**
     * Locates a standalone JRE installation for this JVM. This is the JRE installed
     * outside the JDK installation.
     *
     * @return null if no standalone installation found.
     */
    @Nullable
    private static File getStandaloneJre(File javaHome, OperatingSystem os) {
        if (!os.isWindows()) {
            return null;
        }

        Matcher m = WINDOWS_JDK_DIR_PATTERN.matcher(javaHome.getName());

        if (m.matches()) {
            String version = m.group(1);

            int majorVersion = JavaVersionParser.parseMajorVersion(version);
            if (majorVersion != 5) {
                version = Integer.toString(majorVersion);
            }

            File jreHome = new File(javaHome.getParentFile(), "jre" + version);
            if (jreHome.isDirectory()) {
                return jreHome;
            }
        }

        return null;
    }

    /**
     * Matches a JRE directory followed by the java version.
     */
    private static final Pattern WINDOWS_JRE_DIR_PATTERN = Pattern.compile("jre([\\d._]+)");

    /**
     * Locates a standalone JDK installation for this JVM. This is the JDK installed
     * outside the JRE installation.
     *
     * @return null if no standalone installation found.
     */
    @Nullable
    private static File getStandaloneJdk(File javaHome, OperatingSystem os) {
        if (!os.isWindows()) {
            return null;
        }

        Matcher m = WINDOWS_JRE_DIR_PATTERN.matcher(javaHome.getName());

        if (m.matches()) {
            String version = m.group(1);
            File jdkHome = new File(javaHome.getParentFile(), "jdk" + version);
            if (jdkHome.isDirectory()) {
                return jdkHome;
            }
        }

        return null;
    }
}
