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
import com.google.common.collect.Sets;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.jvm.JavaVersionParser;
import org.gradle.internal.FileUtils;
import org.gradle.internal.os.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Represents a fully probed JVM. This JVM is valid and can execute java code.
 *
 * TODO: We should better integrate this with JvmInstallationMetadata
 */
public class Jvm extends DefaultJavaInfo {

    private static final Logger LOGGER = LoggerFactory.getLogger(Jvm.class);

    private static final HashSet<String> VENDOR_PROPERTIES = Sets.newHashSet("java.vendor", "java.vm.vendor");
    private static final AtomicReference<Jvm> CURRENT = new AtomicReference<Jvm>();
    private static final Pattern APP_NAME_REGEX = Pattern.compile("APP_NAME_\\d+");
    private static final Pattern JAVA_MAIN_CLASS_REGEX = Pattern.compile("JAVA_MAIN_CLASS_\\d+");

    private final boolean currentJvm;
    private final String implementationJavaVersion;
    private final Integer javaVersionMajor;

    public static Jvm current() {
        Jvm jvm = CURRENT.get();
        if (jvm == null) {
            CURRENT.compareAndSet(null, current(OperatingSystem.current()));
            jvm = CURRENT.get();
        }
        return jvm;
    }

    /**
     * Construct a JVM instance by inspecting the current JVM.
     */
    @VisibleForTesting
    static Jvm current(OperatingSystem os) {
        return new Jvm(
            os,
            FileUtils.canonicalize(new File(System.getProperty("java.home"))),
            System.getProperty("java.version"),
            JavaVersionParser.parseMajorVersion(System.getProperty("java.version")),
            true
        );
    }

    /**
     * Constructs a JVM instance from a valid JVM. No additional validation is performed.
     * It is assumed that the JAVA_HOME provided is valid and can execute Java code.
     */
    private Jvm(OperatingSystem os, File javaHome, String implementationJavaVersion, Integer javaVersionMajor, boolean currentJvm) {
        super(javaHome, os);
        this.implementationJavaVersion = implementationJavaVersion;
        this.javaVersionMajor = javaVersionMajor;
        this.currentJvm = currentJvm;
    }

    /**
     * Creates JVM instance for given values. This method is intended to be for java homes that have been
     * probed by the JVM detection infrastructure. No validation is performed on the provided JAVA_HOME.
     */
    public static Jvm discovered(File javaHome, String implementationJavaVersion, Integer javaVersionMajor) {
        Jvm jvm = new Jvm(OperatingSystem.current(), javaHome, implementationJavaVersion, javaVersionMajor, true);
        return orCurrentJvm(jvm);
    }

    /**
     * If the candidate is the same as the current JVM, return the current JVM. Otherwise, return the candidate.
     */
    private static Jvm orCurrentJvm(Jvm candidate) {
        try {
            if (candidate.getJavaHome().getCanonicalFile().equals(Jvm.current().getJavaHome().getCanonicalFile())) {
                return Jvm.current();
            }

            return candidate;
        } catch (IOException e) {
            return candidate;
        }
    }

    /**
     * @deprecated KGP uses this, new code should use {@link #discovered(File, String, Integer)}
     */
    @Deprecated
    public static Jvm discovered(File javaHome, String implementationJavaVersion, JavaVersion javaVersion) {
        return discovered(
            javaHome,
            implementationJavaVersion,
            Integer.parseInt(javaVersion.getMajorVersion())
        );
    }

    @Override
    public String toString() {
        return "Version: " + implementationJavaVersion + ", " + "JAVA_HOME: " + getJavaHome();
    }

    @Override
    protected File getFallbackExecutable(String command, File attempted) {
        if (!currentJvm) {
            return super.getFallbackExecutable(command, attempted);
        }

        // TODO: This is some really wacky behavior. Why do we fallback to looking at the PATH
        // or the current directory for Jvm.current()?
        // We should probably deprecate this or move the logic elsewhere if we want to keep it.

        File pathExecutable = getOs().findInPath(command);
        if (pathExecutable != null) {
            LOGGER.info(String.format("Unable to find the '%s' executable using home: %s. We found it on the PATH: %s.",
                command, getJavaHome(), pathExecutable));
            return pathExecutable;
        }

        LOGGER.warn("Unable to find the '{}' executable. Tried the java home: {} and the PATH."
                + " We will assume the executable can be run in the current working folder.",
            command, getJavaHome());
        return new File(getOs().getExecutableName(command));
    }

    /**
     * @return the major part of the java version if known, otherwise null
     */
    public Integer getJavaVersionMajor() {
        return javaVersionMajor;
    }

    /**
     * @return the {@link JavaVersion} information
     */
    public JavaVersion getJavaVersion() {
        return JavaVersion.toVersion(javaVersionMajor);
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

    // TODO: This is wrong. This is querying whether the current JVM is IBM, not whether the referenced JVM is IBM.
    //       We should get the proper information from the probed JVM metadata
    public boolean isIbmJvm() {
        for (String vendorProperty : VENDOR_PROPERTIES) {
            if (System.getProperties().containsKey(vendorProperty)
                && System.getProperty(vendorProperty).toLowerCase(Locale.ROOT).startsWith("ibm corporation")) {
                return true;
            }
        }
        return false;
    }

    // TODO: This is wrong. This is querying whether the current JVM is the given vendor, not whether the referenced JVM is.
    //       We should get the proper information from the probed JVM metadata
    @Nullable
    public String getVendor() {
        for (String vendorProperty : VENDOR_PROPERTIES) {
            if (System.getProperties().containsKey(vendorProperty) && !System.getProperty(vendorProperty).isEmpty()) {
                return System.getProperty(vendorProperty);
            }
        }
        return null;
    }
}
