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
package org.gradle.api.tasks.wrapper

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.Main
import org.slf4j.Logger
import org.slf4j.LoggerFactory      
import java.util.regex.Pattern
import java.util.regex.Matcher;

/**
 * @author Hans Dockter
 */
public class WindowsExeGenerator {
    static Logger logger = LoggerFactory.getLogger(WindowsExeGenerator);

    private static final Pattern VERSION_PATTERN = ~/^launch4j-base-(.*?).zip$/

    public void generate(String jarPath,  File scriptDestinationDir, File buildDir, AntBuilder ant) {
        File libDir = new File(System.getProperty(Main.GRADLE_HOME_PROPERTY_KEY), "/lib");
        logger.debug("Using lib dir: " + libDir);
        File launch4jBaseZip = new File(libDir, "launch4j").listFiles().find { File file -> file.name.startsWith("launch4j-base") };
        logger.debug("Using base zip: " + launch4jBaseZip);
        Matcher matcher = (launch4jBaseZip.getName() =~ VERSION_PATTERN)
        matcher.matches()
        String version = matcher.group(1)
        logger.debug("Determined launch4j version to: " + version);
        File launch4jDist = new File(buildDir, "launch4j-$version")
        logger.debug("Using base dist: " + launch4jDist);
        ant.sequential {
            unzip(src: launch4jBaseZip, dest: new File(buildDir, "launch4j"))
            String os = [Os.FAMILY_WINDOWS, Os.FAMILY_MAC, Os.FAMILY_UNIX].find {String family ->
                Os.isFamily(family)
            }
            unzip(src: "$libDir/launch4j/launch4j-bin-${os}-${version}.zip", dest: launch4jDist)
            chmod(dir: "$launch4jDist/bin", perm: "ugo+rx", includes: "windres,ld")
            taskdef(name: "launchMe", classname: "net.sf.launch4j.ant.Launch4jTask",
                    classpath: "$launch4jDist/launch4j.jar:$launch4jDist/lib/xstream.jar")
            launchMe() {
                config(headerType: "console", outfile: "$scriptDestinationDir/gradlew.exe",
                        dontWrapJar: "true", jarPath: jarPath) {
                    jre(minVersion: "1.5.0")
                }
            }
        }
    }
}
