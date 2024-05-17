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

public class JansiLibraryFactory {

    public final static String MAC_OSX_LIB_FILENAME = "libjansi.jnilib";
    public final static String LINUX_LIB_FILENAME = "libjansi.so";
    public final static String WINDOWS_LIB_FILENAME = "jansi.dll";
    private JansiRuntimeResolver jansiRuntimeResolver = new DefaultJansiRuntimeResolver();

    void setJansiRuntimeResolver(JansiRuntimeResolver jansiRuntimeResolver) {
        this.jansiRuntimeResolver = jansiRuntimeResolver;
    }

    public JansiLibrary create() {
        String os = jansiRuntimeResolver.getOperatingSystem();
        JansiOperatingSystemSupport osSupport = JansiOperatingSystemSupport.forIdentifier(os);

        if (osSupport == null) {
            return null;
        }

        switch (osSupport) {
            case MAC_OS_X: return new JansiLibrary(os, MAC_OSX_LIB_FILENAME);
            case LINUX: return new JansiLibrary(jansiRuntimeResolver.getPlatform(), LINUX_LIB_FILENAME);
            case WINDOWS: return new JansiLibrary(jansiRuntimeResolver.getPlatform(), WINDOWS_LIB_FILENAME);
            default: return null;
        }
    }
}
