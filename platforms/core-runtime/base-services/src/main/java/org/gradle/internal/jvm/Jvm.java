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

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.jvm.JavaVersionParser;
import org.gradle.internal.FileUtils;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.os.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class Jvm implements JavaInfo {

    private static final Logger LOGGER = LoggerFactory.getLogger(Jvm.class);
    private static final HashSet<String> VENDOR_PROPERTIES = Sets.newHashSet("java.vendor", "java.vm.vendor");
    private static final AtomicReference<Jvm> CURRENT = new AtomicReference<Jvm>();
    private static final Pattern APP_NAME_REGEX = Pattern.compile("APP_NAME_\\d+");
    private static final Pattern JAVA_MAIN_CLASS_REGEX = Pattern.compile("JAVA_MAIN_CLASS_\\d+");

    private final OperatingSystem os;
    //supplied java location
    private final File javaBase;
    //discovered java location
    private final File javaHome;
    private final boolean userSupplied;
    private final String implementationJavaVersion;
    private final Integer javaVersionMajor;

    // Cached resolved executables
    private File javaExecutable;
    private File javacExecutable;
    private File javadocExecutable;
    private Optional<File> toolsJar;
    private Boolean jdk;

    public static Jvm current() {
        Jvm jvm = CURRENT.get();
        if (jvm == null) {
            CURRENT.compareAndSet(null, new Jvm(OperatingSystem.current()));
            jvm = CURRENT.get();
        }
        return jvm;
    }

    private static Jvm create(File javaBase, @Nullable String implementationJavaVersion, @Nullable Integer javaVersionMajor) {
        Jvm jvm = new Jvm(OperatingSystem.current(), javaBase, implementationJavaVersion, javaVersionMajor);
        Jvm current = current();
        return jvm.getJavaHome().equals(current.getJavaHome()) ? current : jvm;
    }

    /**
     * Constructs JVM details by inspecting the current JVM.
     */
    Jvm(OperatingSystem os) {
        this(os, FileUtils.canonicalize(new File(System.getProperty("java.home"))), System.getProperty("java.version"), JavaVersionParser.parseMajorVersion(System.getProperty("java.version")), false);
    }

    /**
     * Constructs JVM details from the given values
     */
    Jvm(OperatingSystem os, File suppliedJavaBase, String implementationJavaVersion, Integer javaVersionMajor) {
        this(os, suppliedJavaBase, implementationJavaVersion, javaVersionMajor, true);
    }

    private Jvm(OperatingSystem os, File suppliedJavaBase, String implementationJavaVersion, Integer javaVersionMajor, boolean userSupplied) {
        this.os = os;
        this.javaBase = suppliedJavaBase;
        this.implementationJavaVersion = implementationJavaVersion;
        this.javaVersionMajor = javaVersionMajor;
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
    public static Jvm discovered(File javaHome, String implementationJavaVersion, Integer javaVersionMajor) {
        return create(javaHome, implementationJavaVersion, javaVersionMajor);
    }

    /**
     * @deprecated KGP uses this, new code should use {@link #discovered(File, String, Integer)}
     */
    @Deprecated
    public static Jvm discovered(File javaHome, String implementationJavaVersion, JavaVersion javaVersion) {
        return create(javaHome, implementationJavaVersion, javaVersion.getMajorVersionNumber());
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
                + " We will assume the executable can be run in the current working folder.",
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
     * @return the major part of the java version if known, otherwise null
     */
    @Nullable
    public Integer getJavaVersionMajor() {
        return javaVersionMajor;
    }

    /**
     * @return the {@link JavaVersion} information
     */
    @Nullable
    public JavaVersion getJavaVersion() {
        return JavaVersion.toVersion(javaVersionMajor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getJavaHome() {
        return javaHome;
    }

    private File findJavaHome(File javaBase) {
        Optional<File> toolsJar = findToolsJar(javaBase);
        if (toolsJar.isPresent()) {
            return toolsJar.get().getParentFile().getParentFile();
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
            return toolsJar.orNull();
        } else {
            toolsJar = findToolsJar(javaHome);
        }

        return toolsJar.orNull();
    }

    /**
     * Locates a stand-alone JRE installation for this JVM. Returns null if not found. This is the JRE installed outside the JDK installation.
     */
    @Nullable
    public File getStandaloneJre() {
        JavaVersion javaVersion = getJavaVersion();
        if (javaVersion != null && javaVersion.isJava9Compatible()) {
            return null;
        }
        if (os.isWindows()) {
            File jreDir;
            if (javaVersionMajor == 5) {
                jreDir = new File(javaHome.getParentFile(), "jre" + implementationJavaVersion);
            } else {
                jreDir = new File(javaHome.getParentFile(), "jre" + javaVersionMajor);
            }
            if (jreDir.isDirectory()) {
                return jreDir;
            }
        }
        return null;
    }

    /**
     * Locates the JRE installation contained within this JVM. Returns null if no JRE installation is available.
     */
    @Nullable
    public File getEmbeddedJre() {
        File jreDir = new File(javaHome, "jre");
        if (jreDir.isDirectory()) {
            return jreDir;
        }
        return null;
    }

    @Nullable
    public File getJre() {
        File standaloneJre = getStandaloneJre();
        if (standaloneJre != null) {
            return standaloneJre;
        }
        return getEmbeddedJre();
    }

    private Optional<File> findToolsJar(File javaHome) {
        File toolsJar = new File(javaHome, "lib/tools.jar");
        if (toolsJar.exists()) {
            return Optional.of(toolsJar);
        }
        if (javaHome.getName().equalsIgnoreCase("jre")) {
            javaHome = javaHome.getParentFile();
            toolsJar = new File(javaHome, "lib/tools.jar");
            if (toolsJar.exists()) {
                return Optional.of(toolsJar);
            }
        }

        if (os.isWindows()) {
            String version = implementationJavaVersion;
            if (javaHome.getName().matches("jre\\d+") || javaHome.getName().equals("jre" + version)) {
                javaHome = new File(javaHome.getParentFile(), "jdk" + version);
                toolsJar = new File(javaHome, "lib/tools.jar");
                if (toolsJar.exists()) {
                    return Optional.of(toolsJar);
                }
            }
        }

        return Optional.absent();
    }

    public static Map<String, ?> getInheritableEnvironmentVariables(Map<String, ?> envVars) {
        Map<String, Object> vars = new HashMap<String, Object>();
        for (Map.Entry<String, ?> entry : envVars.entrySet()) {
            // The following are known variables that can change between builds and should not be inherited
            if (APP_NAME_REGEX.matcher(entry.getKey()).matches()
                || JAVA_MAIN_CLASS_REGEX.matcher(entry.getKey()).matches()
                || entry.getKey().equals("TERM_SESSION_ID")
                || entry.getKey().equals("ITERM_SESSION_ID")) {
                continue;
            }
            vars.put(entry.getKey(), entry.getValue());
        }
        return vars;
    }

    public boolean isIbmJvm() {
        for (String vendorProperty : VENDOR_PROPERTIES) {
            if (System.getProperties().containsKey(vendorProperty)
                && System.getProperty(vendorProperty).toLowerCase(Locale.ROOT).startsWith("ibm corporation")) {
                return true;
            }
        }
        return false;
    }

}
