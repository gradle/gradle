/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.wrapper;

import java.util.Properties;
import java.io.File;

/**
 * @author Hans Dockter
 */
class InstallMain {
    public static final String ALWAYS_INSTALL_ENV = "GRADLE_WRAPPER_ALWAYS_INSTALL";
    
    /*
     * First argument: urlRoot (e.g. http://dist.codehaus.org/gradle)
     * Second argument: distribution-name (e.g. gradle-1.0)
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Wrong number of arguments supplied!");
        }
        boolean alwaysInstall = Boolean.parseBoolean(System.getenv(ALWAYS_INSTALL_ENV));
        new Install(alwaysInstall).createDist(args[0], args[1], new File(Install.WRAPPER_DIR, "gradle-dist"));
    }

}
