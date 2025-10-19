/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.internal.nativeintegration.jna;

import org.gradle.internal.nativeintegration.EnvironmentModificationResult;
import org.gradle.internal.nativeintegration.NativeIntegrationException;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.nativeintegration.ReflectiveEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Windows-specific fallback for environment variable modification when native-platform is unavailable.
 *
 * <p>This implementation uses Java reflection via {@link ReflectiveEnvironment} to modify
 * environment variables without requiring native binaries. This is particularly useful on
 * Windows ARM64 where native-platform does not provide native libraries.</p>
 *
 * <h3>Limitations:</h3>
 * <ul>
 *   <li>Cannot change process working directory (returns false from {@link #maybeSetProcessDir(File)})</li>
 *   <li>Only works on Windows platform</li>
 *   <li>Cannot detach process from console</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * This class is automatically used by {@code NativeServices} when:
 * <ul>
 *   <li>The platform is Windows</li>
 *   <li>Native-platform initialization fails</li>
 * </ul>
 *
 * @see ReflectiveEnvironment
 * @see ProcessEnvironment
 */
public class WindowsReflectiveProcessEnvironment implements ProcessEnvironment {
    private static final Logger LOGGER = LoggerFactory.getLogger(WindowsReflectiveProcessEnvironment.class);

    private final ReflectiveEnvironment reflectiveEnvironment = new ReflectiveEnvironment();
    private final Long pid;

    public WindowsReflectiveProcessEnvironment() {
        this.pid = extractPIDFromRuntimeMXBeanName();
    }

    /**
     * The default format of the name of the Runtime MX bean is PID@HOSTNAME.
     * The PID is parsed assuming that is the format.
     *
     * This works on Windows and should work with any Java VM.
     */
    private Long extractPIDFromRuntimeMXBeanName() {
        Long pid = null;
        String runtimeMXBeanName = ManagementFactory.getRuntimeMXBean().getName();
        int separatorPos = runtimeMXBeanName.indexOf('@');
        if (separatorPos > -1) {
            try {
                pid = Long.parseLong(runtimeMXBeanName.substring(0, separatorPos));
            } catch (NumberFormatException e) {
                LOGGER.debug("Failed to parse PID from Runtime MX bean name: " + runtimeMXBeanName, e);
            }
        } else {
            LOGGER.debug("Failed to parse PID from Runtime MX bean name: no '@' separator found");
        }
        return pid;
    }

    @Override
    public EnvironmentModificationResult maybeSetEnvironment(Map<String, String> source) {
        try {
            LOGGER.debug("Setting {} environment variables using reflection", source.size());

            // Get current environment variables
            Map<String, String> currentEnv = System.getenv();
            Set<String> keysToRemove = new HashSet<>(currentEnv.keySet());

            // Remove keys that are not in the source
            keysToRemove.removeAll(source.keySet());
            for (String key : keysToRemove) {
                reflectiveEnvironment.unsetenv(key);
            }

            // Set all environment variables from source
            for (Map.Entry<String, String> entry : source.entrySet()) {
                reflectiveEnvironment.setenv(entry.getKey(), entry.getValue());
            }

            LOGGER.debug("Successfully set environment variables using reflection");
            return EnvironmentModificationResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.debug("Failed to set environment variables using reflection", e);
            return EnvironmentModificationResult.UNSUPPORTED_ENVIRONMENT;
        }
    }

    @Override
    public void removeEnvironmentVariable(String name) throws NativeIntegrationException {
        try {
            reflectiveEnvironment.unsetenv(name);
        } catch (Exception e) {
            throw new NativeIntegrationException("Failed to remove environment variable: " + name, e);
        }
    }

    @Override
    public EnvironmentModificationResult maybeRemoveEnvironmentVariable(String name) {
        try {
            reflectiveEnvironment.unsetenv(name);
            return EnvironmentModificationResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.debug("Failed to remove environment variable: " + name, e);
            return EnvironmentModificationResult.UNSUPPORTED_ENVIRONMENT;
        }
    }

    @Override
    public void setEnvironmentVariable(String name, String value) throws NativeIntegrationException {
        try {
            if (value == null) {
                reflectiveEnvironment.unsetenv(name);
            } else {
                reflectiveEnvironment.setenv(name, value);
            }
        } catch (Exception e) {
            throw new NativeIntegrationException("Failed to set environment variable: " + name, e);
        }
    }

    @Override
    public EnvironmentModificationResult maybeSetEnvironmentVariable(String name, String value) {
        try {
            if (value == null) {
                reflectiveEnvironment.unsetenv(name);
            } else {
                reflectiveEnvironment.setenv(name, value);
            }
            return EnvironmentModificationResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.debug("Failed to set environment variable: " + name, e);
            return EnvironmentModificationResult.UNSUPPORTED_ENVIRONMENT;
        }
    }

    @Override
    public File getProcessDir() throws NativeIntegrationException {
        // We can still get the current working directory using standard Java
        return new File(System.getProperty("user.dir"));
    }

    @Override
    public void setProcessDir(File processDir) throws NativeIntegrationException {
        throw new NativeIntegrationException("Cannot change process working directory using reflection on Windows");
    }

    @Override
    public boolean maybeSetProcessDir(File processDir) {
        // Cannot change process directory using reflection
        return false;
    }

    @Override
    public Long getPid() throws NativeIntegrationException {
        if (pid != null) {
            return pid;
        }
        throw new NativeIntegrationException("Unable to determine process PID");
    }

    @Override
    public Long maybeGetPid() {
        return pid;
    }

    @Override
    public boolean maybeDetachProcess() {
        // Cannot detach process using reflection
        return false;
    }

    @Override
    public void detachProcess() throws NativeIntegrationException {
        throw new NativeIntegrationException("Cannot detach process using reflection on Windows");
    }
}
