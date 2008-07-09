/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.util;

import org.apache.tools.ant.taskdefs.Jar;
import org.apache.tools.ant.taskdefs.Zip;
import org.apache.tools.ant.taskdefs.GUnzip;
import org.apache.tools.ant.taskdefs.Expand;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class CompressUtil {
    public static void unzip(File source, File destination) {
        Expand unzip = new Expand();
        unzip.setSrc(source);
        unzip.setDest(destination);

        AntUtil.execute(unzip);
    }

    public static void zip(File baseDir, File destination) {
        Zip zip = new Zip();
        zip.setBasedir(baseDir);
        zip.setDestFile(destination);
        AntUtil.execute(zip);
    }
}
