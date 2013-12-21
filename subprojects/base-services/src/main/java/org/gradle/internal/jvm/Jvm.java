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

import org.gradle.api.JavaVersion;
import org.gradle.api.Nullable;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.os.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
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
    private final JavaVersion javaVersion;
    private static final AtomicReference<Jvm> CURRENT = new AtomicReference<Jvm>();

    public static Jvm current() {
        Jvm jvm = CURRENT.get();
        if (jvm == null) {
            CURRENT.compareAndSet(null, create(null));
            jvm = CURRENT.get();
        }
        return jvm;
    }

    static Jvm create(File javaBase) {
        String vendor = System.getProperty("java.vm.vendor");
        if (vendor.toLowerCase().startsWith("apple inc.")) {
            return new AppleJvm(OperatingSystem.current(), javaBase);
        }
        if (vendor.toLowerCase().startsWith("ibm corporation")) {
            return new IbmJvm(OperatingSystem.current(), javaBase);
        }
        return new Jvm(OperatingSystem.current(), javaBase);
    }

    Jvm(OperatingSystem os) {
        this(os, null);
    }

    /**
     * @param os the OS
     * @param suppliedJavaBase initial location to discover from. May be jdk or jre.
     */
    Jvm(OperatingSystem os, File suppliedJavaBase) {
        this.os = os;
        if (suppliedJavaBase == null) {
            //discover based on what's in the sys. property
            try {
                this.javaBase = new File(System.getProperty("java.home")).getCanonicalFile();
            } catch (IOException e) {
                throw new UncheckedException(e);
            }
            this.javaHome = findJavaHome(javaBase);
            this.javaVersion = JavaVersion.current();
            this.userSupplied = false;
        } else {
            //precisely use what the user wants and validate strictly further on
            this.javaBase = suppliedJavaBase;
            this.javaHome = suppliedJavaBase;
            this.userSupplied = true;
            this.javaVersion = null;
        }
    }
    
    /**
     * Creates jvm instance for given java home. Attempts to validate if provided javaHome is a valid jdk or jre location.
     *
     * @param javaHome - location of your jdk or jre (jdk is safer), cannot be null
     * @return jvm for given java home
     *
     * @throws org.gradle.internal.jvm.JavaHomeException when supplied javaHome does not seem to be a valid jdk or jre location
     * @throws IllegalArgumentException when supplied javaHome is not a valid folder
     */
    public static JavaInfo forHome(File javaHome) throws JavaHomeException, IllegalArgumentException {
        if (javaHome == null || !javaHome.isDirectory()) {
            throw new IllegalArgumentException("Supplied javaHome must be a valid directory. You supplied: " + javaHome);
        }
        Jvm jvm = create(javaHome);
        //some validation:
        jvm.getJavaExecutable();
        return jvm;
    }

    @Override
    public String toString() {
        if (userSupplied) {
            return "User-supplied java: " + javaBase;
        }
        return String.format("%s (%s %s)", SystemProperties.getJavaVersion(), System.getProperty("java.vm.vendor"), System.getProperty("java.vm.version"));
    }

    private File findExecutable(String command) {
        File exec = new File(getJavaHome(), "bin/" + command);
        File executable = new File(os.getExecutableName(exec.getAbsolutePath()));
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
    public File getJavaExecutable() throws JavaHomeException {
        return findExecutable("java");
    }

    public File getJavacExecutable() throws JavaHomeException {
        return findExecutable("javac");
    }

    /**
     * {@inheritDoc}
     */
    public File getJavadocExecutable() throws JavaHomeException {
        return findExecutable("javadoc");
    }

    /**
     * {@inheritDoc}
     */
    public File getExecutable(String name) throws JavaHomeException {
        return findExecutable(name);
    }

    /**
     * @return the {@link JavaVersion} information
     */
    public JavaVersion getJavaVersion() {
        return javaVersion;
    }

    /**
     * {@inheritDoc}
     */
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
    public File getRuntimeJar() {
        File runtimeJar = new File(javaBase, "lib/rt.jar");
        if (runtimeJar.exists()) {
            return runtimeJar;
        }
        runtimeJar = new File(javaBase, "jre/lib/rt.jar");
        return runtimeJar.exists() ? runtimeJar : null;
    }

    /**
     * {@inheritDoc}
     */
    public File getToolsJar() {
        return findToolsJar(javaBase);
    }

    /**
     * Locates a stand-alone JRE installation for this JVM. Returns null if not found.
     */
    @Nullable
    public Jre getStandaloneJre() {
        if (os.isWindows()) {
            File jreDir;
            if (javaVersion.isJava5()) {
                jreDir = new File(javaHome.getParentFile(), String.format("jre%s", SystemProperties.getJavaVersion()));
            } else {
                jreDir = new File(javaHome.getParentFile(), String.format("jre%s", javaVersion.getMajorVersion()));
            }
            if (jreDir.isDirectory()) {
                return new DefaultJre(jreDir);
            }
        }
        if (!new File(javaHome, "jre").isDirectory()) {
            return new DefaultJre(javaHome);
        }
        return null;
    }

    /**
     * Locates the JRE installation for this JVM.
     */
    public Jre getJre() {
        File jreDir = new File(javaBase, "jre");
        if (jreDir.isDirectory()) {
            return new DefaultJre(jreDir);
        }
        return new DefaultJre(javaBase);
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
            String version = SystemProperties.getJavaVersion();
            if (javaHome.getName().matches("jre\\d+") || javaHome.getName().equals(String.format("jre%s", version))) {
                javaHome = new File(javaHome.getParentFile(), String.format("jdk%s", version));
                toolsJar = new File(javaHome, "lib/tools.jar");
                if (toolsJar.exists()) {
                    return toolsJar;
                }
            }
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, ?> getInheritableEnvironmentVariables(Map<String, ?> envVars) {
        return envVars;
    }

    public boolean isIbmJvm() {
        return false;
    }

    static class IbmJvm extends Jvm {
        IbmJvm(OperatingSystem os, File suppliedJavaBase) {
            super(os, suppliedJavaBase);
        }

        @Override
        public boolean isIbmJvm() {
            return true;
        }
    }

    /**
     * Note: Implementation assumes that an Apple JVM always comes with a JDK rather than a JRE,
     * but this is likely an over-simplification.
     */
    static class AppleJvm extends Jvm {
        AppleJvm(OperatingSystem os) {
            super(os);
        }

        AppleJvm(OperatingSystem current, File javaHome) {
            super(current, javaHome);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public File getRuntimeJar() {
            File javaHome = super.getJavaHome();
            File runtimeJar = new File(javaHome.getParentFile(), "Classes/classes.jar");
            return runtimeJar.exists() ? runtimeJar : null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public File getToolsJar() {
            return getRuntimeJar();
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
