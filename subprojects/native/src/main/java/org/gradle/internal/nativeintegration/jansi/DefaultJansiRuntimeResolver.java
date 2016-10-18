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

import static org.gradle.internal.nativeintegration.jansi.JansiOperatingSystemSupport.*;

/**
 * Portions of this class have been copied from org.fusesource.hawtjni.runtime.Library.java,
 * a class available in the project HawtJNI licensed with Eclipse Public License v1.0.
 *
 * @see <a href="https://github.com/fusesource/hawtjni">HawtJNI</a>
 */
public class DefaultJansiRuntimeResolver implements JansiRuntimeResolver {

    @Override
    public String getOperatingSystem() {
        String name = System.getProperty("os.name").toLowerCase().trim();

        if (name.startsWith("linux")) {
            return LINUX.getIdentifier();
        }
        if (name.startsWith("mac os x")) {
            return MAC_OS_X.getIdentifier();
        }
        if (name.startsWith("win")) {
            return WINDOWS.getIdentifier();
        }
        return name.replaceAll("\\W+", "_");
    }

    @Override
    public String getPlatform() {
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
