/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.api.JavaVersion;
import org.gradle.internal.FileUtils;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.os.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class Jvm implements JavaInfo {

    private final static Logger LOGGER = LoggerFactory.getLogger(Jvm.class);

    private final OperatingSystem os;
    //supplied java location
    private final File javaBase;
    //discovered java location
    private final File javaHome;
    private final boolean userSupplied;
    private final String implementationJavaVersion;
    private final JavaVersion javaVersion;
    private static final AtomicReference<JvmImplementation> CURRENT = new AtomicReference<JvmImplementation>();

    // Cached resolved executables
    private File javaExecutable;
    private File javacExecutable;
    private File javadocExecutable;
    private File toolsJar;
    private Boolean jdk;

    public static Jvm current() {
        Jvm jvm = CURRENT.get();
        if (jvm == null) {
            CURRENT.compareAndSet(null, createCurrent());
            jvm = CURRENT.get();
        }
        return jvm;
    }

    @VisibleForTesting
    static JvmImplementation createCurrent() {
        String vendor = System.getProperty("java.vm.vendor");
        if (vendor.toLowerCase().startsWith("apple inc.")) {
            return new AppleJvm(OperatingSystem.current());
        }
        if (vendor.toLowerCase().startsWith("ibm corporation")) {
            return new IbmJvm(OperatingSystem.current());
        }
        return new JvmImplementation(OperatingSystem.current());
    }

    private static Jvm create(File javaBase, @Nullable String implementationJavaVersion, @Nullable JavaVersion javaVersion) {
        Jvm jvm = new Jvm(OperatingSystem.current(), javaBase, implementationJavaVersion, javaVersion);
        Jvm current = current();
        return jvm.getJavaHome().equals(current.getJavaHome()) ? current : jvm;
    }

    /**
     * Constructs JVM details by inspecting the current JVM.
     */
    Jvm(OperatingSystem os) {
        this(os, FileUtils.canonicalize(new File(System.getProperty("java.home"))), System.getProperty("java.version"), JavaVersion.current(), false);
    }

    /**
     * Constructs JVM details from the given values
     */
    Jvm(OperatingSystem os, File suppliedJavaBase, String implementationJavaVersion, JavaVersion javaVersion) {
        this(os, suppliedJavaBase, implementationJavaVersion, javaVersion, true);
    }

    private Jvm(OperatingSystem os, File suppliedJavaBase, String implementationJavaVersion, JavaVersion javaVersion, boolean userSupplied) {
        this.os = os;
        this.javaBase = suppliedJavaBase;
        this.implementationJavaVersion = implementationJavaVersion;
        this.javaVersion = javaVersion;
        this.userSupplied = userSupplied;
        this.javaHome = findJavaHome(suppliedJavaBase);
    }

    /**
     * Creates JVM instance for given java home. Attempts to validate if provided javaHome is a valid jdk or jre location.
     * This method is intended to be used for user supplied java homes.
     *
     * @param javaHome - location of your jdk or jre (jdk is safer), cannot be null
     * @return jvm for given java home
     * @throws org.gradle.internal.jvm.JavaHomeException when supplied javaHome does not seem to be a valid jdk or jre location
     * @throws IllegalArgumentException when supplied javaHome is not a valid folder
     */
    public static JavaInfo forHome(File javaHome) throws JavaHomeException, IllegalArgumentException {
        if (javaHome == null || !javaHome.isDirectory()) {
            throw new IllegalArgumentException("Supplied javaHome must be a valid directory. You supplied: " + javaHome);
        }
        Jvm jvm = create(javaHome, null, null);
        //some validation:
        jvm.getJavaExecutable();
        return jvm;
    }

    /**
     * Creates JVM instance for given values. This method is intended to be used for discovered java homes.
     */
    public static Jvm discovered(File javaHome, String implementationJavaVersion, JavaVersion javaVersion) {
        return create(javaHome, implementationJavaVersion, javaVersion);
    }

    @Override
    public String toString() {
        if (userSupplied) {
            return "User-supplied java: " + javaBase;
        }
        return SystemProperties.getInstance().getJavaVersion() + " (" + System.getProperty("java.vm.vendor") + " " + System.getProperty("java.vm.version") + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        Jvm other = (Jvm) obj;
        return other.javaHome.equals(javaHome);
    }

    @Override
    public int hashCode() {
        return javaHome.hashCode();
    }

    public boolean isJdk() {
        if (jdk == null) {
            jdk = findExecutableInJavaHome("javac") != null;
        }
        return jdk;
    }

    @Nullable
    private File findExecutableInJavaHome(String command) {
        File executable = commandLocation(command);
        if (executable.isFile()) {
            return executable;
        }
        return null;
    }

    private File commandLocation(String command) {
        File exec = new File(getJavaHome(), "bin/" + command);
        return new File(os.getExecutableName(exec.getAbsolutePath()));
    }

    private File findExecutable(String command) {
        File executable = commandLocation(command);
        if (executable.isFile()) {
            return executable;
        }

        if (userSupplied) { //then we want to validate strictly
            throw new JavaHomeException(String.format("The supplied javaHome seems to be invalid."
                + " I cannot find the %s executable. Tried location: %s", command, executable.getAbsolutePath()));
        }

        File pathExecutable = os.findInPath(command);
        if (pathExecutable != null) {
            LOGGER.info(String.format("Unable to find the '%s' executable using home: %s. We found it on the PATH: %s.",
                command, getJavaHome(), pathExecutable));
            return pathExecutable;
        }

        LOGGER.warn("Unable to find the '{}' executable. Tried the java home: {} and the PATH."
                + " We will assume the executable can be ran in the current working folder.",
            command, getJavaHome());
        return new File(os.getExecutableName(command));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getJavaExecutable() throws JavaHomeException {
        if (javaExecutable != null) {
            return javaExecutable;
        }
        javaExecutable = findExecutable("java");
        return javaExecutable;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getJavacExecutable() throws JavaHomeException {
        if (javacExecutable != null) {
            return javacExecutable;
        }
        javacExecutable = findExecutable("javac");
        return javacExecutable;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getJavadocExecutable() throws JavaHomeException {
        if (javadocExecutable != null) {
            return javadocExecutable;
        }
        javadocExecutable = findExecutable("javadoc");
        return javadocExecutable;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getExecutable(String name) throws JavaHomeException {
        return findExecutable(name);
    }

    /**
     * @return the {@link JavaVersion} information
     */
    @Nullable
    public JavaVersion getJavaVersion() {
        return javaVersion;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getJavaHome() {
        return javaHome;
    }

    private File findJavaHome(File javaBase) {
        File toolsJar = findToolsJar(javaBase);
        if (toolsJar != null) {
            return toolsJar.getParentFile().getParentFile();
        } else if (javaBase.getName().equalsIgnoreCase("jre") && new File(javaBase.getParentFile(), "bin/java").exists()) {
            return javaBase.getParentFile();
        } else {
            return javaBase;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getToolsJar() {
        if (toolsJar != null) {
            return toolsJar;
        }
        toolsJar = findToolsJar(javaHome);
        return toolsJar;
    }

    /**
     * Locates a stand-alone JRE installation for this JVM. Returns null if not found. This is the JRE installed outside the JDK installation.
     */
    @Nullable
    public Jre getStandaloneJre() {
        if (javaVersion.isJava9Compatible()) {
            return null;
        }
        if (os.isWindows()) {
            File jreDir;
            if (javaVersion.isJava5()) {
                jreDir = new File(javaHome.getParentFile(), "jre" + implementationJavaVersion);
            } else {
                jreDir = new File(javaHome.getParentFile(), "jre" + javaVersion.getMajorVersion());
            }
            if (jreDir.isDirectory()) {
                return new DefaultJre(jreDir);
            }
        }
        return null;
    }

    /**
     * Locates the JRE installation contained within this JVM. Returns null if no JRE installation is available.
     */
    @Nullable
    public Jre getEmbeddedJre() {
        File jreDir = new File(javaHome, "jre");
        if (jreDir.isDirectory()) {
            return new DefaultJre(jreDir);
        }
        return null;
    }

    @Nullable
    public Jre getJre() {
        Jre standaloneJre = getStandaloneJre();
        if (standaloneJre != null) {
            return standaloneJre;
        }
        Jre embeddedJre = getEmbeddedJre();
        if (embeddedJre != null) {
            return embeddedJre;
        }
        return null;
    }

    private File findToolsJar(File javaHome) {
        File toolsJar = new File(javaHome, "lib/tools.jar");
        if (toolsJar.exists()) {
            return toolsJar;
        }
        if (javaHome.getName().equalsIgnoreCase("jre")) {
            javaHome = javaHome.getParentFile();
            toolsJar = new File(javaHome, "lib/tools.jar");
            if (toolsJar.exists()) {
                return toolsJar;
            }
        }

        if (os.isWindows()) {
            String version = implementationJavaVersion;
            if (javaHome.getName().matches("jre\\d+") || javaHome.getName().equals("jre" + version)) {
                javaHome = new File(javaHome.getParentFile(), "jdk" + version);
                toolsJar = new File(javaHome, "lib/tools.jar");
                if (toolsJar.exists()) {
                    return toolsJar;
                }
            }
        }

        return null;
    }

    public Map<String, ?> getInheritableEnvironmentVariables(Map<String, ?> envVars) {
        return envVars;
    }

    public boolean isIbmJvm() {
        return false;
    }

    /**
     * Details about a known JVM implementation.
     */
    static class JvmImplementation extends Jvm {
        JvmImplementation(OperatingSystem os) {
            super(os);
        }
    }

    static class IbmJvm extends JvmImplementation {
        IbmJvm(OperatingSystem os) {
            super(os);
        }

        @Override
        public boolean isIbmJvm() {
            return true;
        }
    }

    /**
     * Note: Implementation assumes that an Apple JVM always comes with a JDK rather than a JRE, but this is likely an over-simplification.
     */
    static class AppleJvm extends JvmImplementation {
        AppleJvm(OperatingSystem os) {
            super(os);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public File getToolsJar() {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Map<String, ?> getInheritableEnvironmentVariables(Map<String, ?> envVars) {
            Map<String, Object> vars = new HashMap<String, Object>();
            for (Map.Entry<String, ?> entry : envVars.entrySet()) {
                if (entry.getKey().matches("APP_NAME_\\d+") || entry.getKey().matches("JAVA_MAIN_CLASS_\\d+")) {
                    continue;
                }
                vars.put(entry.getKey(), entry.getValue());
            }
            return vars;
        }
    }

    private static class DefaultJre extends Jre {
        private final File jreDir;

        public DefaultJre(File jreDir) {
            this.jreDir = jreDir;
        }

        @Override
        public File getHomeDir() {
            return jreDir;
        }
    }
}
