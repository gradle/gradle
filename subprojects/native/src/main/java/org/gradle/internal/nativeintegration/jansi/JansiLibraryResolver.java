/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.nativeintegration.jansi;

/**
 * Portions of this class have been copied from org.fusesource.hawtjni.runtime.Library.java,
 * a class available in the project HawtJNI licensed with Eclipse Public License v1.0.
 *
 * @see <a href="https://github.com/fusesource/hawtjni">HawtJNI</a>
 */
public class JansiLibraryResolver {

    public final static String MAC_OSX_LIB_FILENAME = "libjansi.jnilib";
    public final static String LINUX_LIB_FILENAME = "libjansi.so";
    public final static String WINDOWS_LIB_FILENAME = "jansi.dll";

    public JansiLibrary resolve() {
        String os = getOperatingSystem();
        JansiOperatingSystemSupport osSupport = JansiOperatingSystemSupport.forIdentifier(os);

        switch (osSupport) {
            case MAC_OS_X: return new JansiLibrary(os, MAC_OSX_LIB_FILENAME);
            case LINUX: return new JansiLibrary(getPlatform(), LINUX_LIB_FILENAME);
            case WINDOWS: return new JansiLibrary(getPlatform(), WINDOWS_LIB_FILENAME);
            default: return null;
        }
    }

    private String getOperatingSystem() {
        String name = System.getProperty("os.name").toLowerCase().trim();

        if (name.startsWith("linux") ) {
            return "linux";
        }
        if (name.startsWith("mac os x") ) {
            return "osx";
        }
        if (name.startsWith("win") ) {
            return "windows";
        }
        return name.replaceAll("\\W+", "_");

    }

    private String getPlatform() {
        return getOperatingSystem() + getBitModel();
    }

    private int getBitModel() {
        String prop = System.getProperty("sun.arch.data.model");
        if (prop == null) {
            prop = System.getProperty("com.ibm.vm.bitmode");
        }
        if (prop != null) {
            return Integer.parseInt(prop);
        }
        return -1;
    }
}
