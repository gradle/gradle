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
package org.gradle.api.testing.execution.control.client;

import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * The ForkLaunchMain class bootstraps the classloaders of a forked process, instantiates a class in the control
 * classloader and calss execute on it. <p/> The ForkLaunchMain takes in two parameters: - the config file that contains
 * the classloader configuration for this fork. - the control class to instanciate and call execute on (has to implement
 * ForkExecuter). <p/> The config file should follow the following specification: [shared] (absolute jar file path)*
 * (one a line) [control] (absolute jar file path)* (one a line) [sandbox] (absolute jar file path)* (one a line)
 * [arguments] (any value)* (one a line) <p/> The 'shared' classloader is setup and serves as a parent for the 'control'
 * and 'sandbox' classloaders.
 *
 * @author Tom Eyckmans
 */
public class ForkLaunchMain {

    public static void main(String[] args) throws IOException {
        if (args == null) {
            throw new NullPointerException("args");
        }
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "args.length != 1, expecting 1 argument (the control class to instanciate)!");
        }

        final String configFile = IOUtils.toString(System.in);

        final String executerClassName = args[0];
        if ("".equals(executerClassName)) {
            throw new IllegalArgumentException("executer class to instanciate is empty");
        }

        final ForkLaunchMain forkLaunchMain = new ForkLaunchMain();

        forkLaunchMain.loadConfigFile(configFile);

        forkLaunchMain.readConfigFileContents();

        forkLaunchMain.prepareClassLoaders();

        forkLaunchMain.instanciateForkExecuter(executerClassName);

        forkLaunchMain.execute();
    }

    private static final String SHARED_CONFIG_SECTION = "[shared]";
    private static final String CONTROL_CONFIG_SECTION = "[control]";
    private static final String SANDBOX_CONFIG_SECTION = "[sandbox]";
    private static final String ARGUMENTS_CONFIG_SECTION = "[arguments]";

    private String configFileContents;

    private final List<File> sharedPaths = new ArrayList<File>();
    private final List<File> controlPaths = new ArrayList<File>();
    private final List<File> sandboxPaths = new ArrayList<File>();
    private final List<String> arguments = new ArrayList<String>();

    private ClassLoader sharedClassLoader;
    private ClassLoader controlClassLoader;
    private ClassLoader sandboxClassLoader;

    private Class<?> classToLaunch;
    private Object forkExecuter;

    void loadConfigFile(String configFileContents) {
        if (configFileContents == null) {
            throw new NullPointerException("configFileContents");
        }

        this.configFileContents = configFileContents;
    }

    void readConfigFileContents() {
        BufferedReader configFileReader = null;
        try {
            configFileReader = new BufferedReader(new StringReader(configFileContents));

            String currentConfigSection = null;
            String configFileLine = null;
            while ((configFileLine = configFileReader.readLine()) != null) {
                if (configFileLine.startsWith("[")) {
                    // new configSection
                    currentConfigSection = configFileLine.trim().toLowerCase();
                } else {
                    // classpath element
                    if (currentConfigSection == null) {
                        throw new IllegalArgumentException("invalid config file, doesn't start with a config section!");
                    }

                    if (currentConfigSection.equals(ARGUMENTS_CONFIG_SECTION)) {
                        arguments.add(configFileLine);
                    } else {
                        final File pathElement = new File(configFileLine.trim()).getAbsoluteFile();
                        if (!pathElement.exists()) {
                            throw new IllegalArgumentException(
                                    "path element [" + pathElement.getAbsolutePath() + "] doesn't exists in "
                                            + currentConfigSection + "!");
                        }

                        if (currentConfigSection.equals(SHARED_CONFIG_SECTION)) {
                            sharedPaths.add(pathElement);
                        } else if (currentConfigSection.equals(CONTROL_CONFIG_SECTION)) {
                            controlPaths.add(pathElement);
                        } else if (currentConfigSection.equals(SANDBOX_CONFIG_SECTION)) {
                            sandboxPaths.add(pathElement);
                        } else {
                            throw new IllegalArgumentException(
                                    "invalid config file, contains an unsupported config section "
                                            + currentConfigSection + "!");
                        }
                    }
                }
            }

            if (currentConfigSection == null) {
                throw new IllegalArgumentException("invalid config file, doesn't start with a config section!");
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to read config file contents", e);
        } finally {
            closeQuietly(configFileReader);
        }
    }

    void prepareClassLoaders() {
        final ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader().getParent();

        final URL[] sharedUrls = toUrlArray(SHARED_CONFIG_SECTION, sharedPaths);
        if (sharedUrls.length == 0) {
            sharedClassLoader = systemClassLoader;
        } else {
            sharedClassLoader = new URLClassLoader(sharedUrls, systemClassLoader);
        }

        final URL[] controlUrls = toUrlArray(CONTROL_CONFIG_SECTION, controlPaths);
        controlClassLoader = new URLClassLoader(controlUrls, sharedClassLoader);

        final URL[] sandboxUrls = toUrlArray(SHARED_CONFIG_SECTION, sandboxPaths);
        sandboxClassLoader = new URLClassLoader(sandboxUrls, sharedClassLoader);
    }

    void instanciateForkExecuter(final Object forkExecuter) {
        if (forkExecuter == null) {
            throw new NullPointerException("forkExecuter");
        }

        this.forkExecuter = forkExecuter;
    }

    void instanciateForkExecuter(final String executerClassName) {
        try {
            classToLaunch = controlClassLoader.loadClass(executerClassName);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("failed to load classToLaunch [" + executerClassName + "]", e);
        }

        try {
            forkExecuter = classToLaunch.newInstance();
        } catch (InstantiationException e) {
            throw new RuntimeException("failed to create an instance of the fork executer", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("failed to create an instance of the fork executer", e);
        }
    }

    void execute() {
        final ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(controlClassLoader);
            try {
                classToLaunch.getMethod("setSharedClassLoader", ClassLoader.class).invoke(forkExecuter,
                        sharedClassLoader);
                classToLaunch.getMethod("setControlClassLoader", ClassLoader.class).invoke(forkExecuter,
                        controlClassLoader);
                classToLaunch.getMethod("setSandboxClassLoader", ClassLoader.class).invoke(forkExecuter,
                        sandboxClassLoader);
                classToLaunch.getMethod("setArguments", List.class).invoke(forkExecuter, arguments);

                classToLaunch.getMethod("execute").invoke(forkExecuter);
            } catch (Throwable t) {
                throw new RuntimeException("failed to execute", t);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        }
    }

    void closeQuietly(BufferedReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    void closeQuietly(BufferedWriter writer) {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    URL[] toUrlArray(String configSection, List<File> paths) {
        final List<URL> urls = new ArrayList<URL>();
        for (final File path : paths) {
            try {
                urls.add(path.toURI().toURL());
            } catch (MalformedURLException e) {
                throw new RuntimeException(
                        configSection + " failed to convert [" + path.getAbsolutePath() + "] into a URL", e);
            }
        }
        return urls.toArray(new URL[urls.size()]);
    }

    List<String> getArguments() {
        return arguments;
    }

    ClassLoader getSharedClassLoader() {
        return sharedClassLoader;
    }

    ClassLoader getControlClassLoader() {
        return controlClassLoader;
    }

    ClassLoader getSandboxClassLoader() {
        return sandboxClassLoader;
    }
}
