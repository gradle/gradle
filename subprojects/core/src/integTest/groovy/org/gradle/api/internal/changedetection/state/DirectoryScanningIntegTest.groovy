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

package org.gradle.api.internal.changedetection.state

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.util.GFileUtils
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

// This fails with Java 1.8.0_05, but succeeds with 1.8.0_74
@Requires(adhoc = { TestPrecondition.JDK7_OR_LATER.isFulfilled() && System.getProperty('java.version') != '1.8.0_05' })
class DirectoryScanningIntegTest extends DaemonIntegrationSpec {

    def setup() {
        file('buildSrc/build.gradle') << '''
apply plugin: 'java'

repositories {
    jcenter()
}

dependencies {
    compile 'net.bytebuddy:byte-buddy:1.3.4'
    compile 'net.bytebuddy:byte-buddy-agent:1.3.4'
    compile gradleApi()
}
'''
        file('buildSrc/src/main/java/gradle/advice/DirectoryScanningInterceptor.java') << '''
package gradle.advice;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.matcher.ElementMatchers;
import org.gradle.api.internal.file.collections.DirectoryFileTree;

public class DirectoryScanningInterceptor {
    public static void install() {
        ByteBuddyAgent.install();

        ClassLoader targetClassLoader = DirectoryFileTree.class.getClassLoader();

        // interceptor class must be injected to the same classloader as the target class that is intercepted
        new ByteBuddy().redefine(CountDirectoryScans.class)
                .make()
                .load(targetClassLoader,
                        ClassReloadingStrategy.fromInstalledAgent());

        new ByteBuddy().redefine(DirectoryFileTree.class)
                .visit(new AsmVisitorWrapper.ForDeclaredMethods().writerFlags(ClassWriter.COMPUTE_FRAMES).method(ElementMatchers.named("visitFrom"), Advice.to(CountDirectoryScans.class)))
                .make()
                .load(targetClassLoader,
                        ClassReloadingStrategy.fromInstalledAgent());
    }
}
'''
        file("buildSrc/src/main/java/gradle/advice/CountDirectoryScans.java") << '''
package gradle.advice;

import net.bytebuddy.asm.Advice;
import java.util.*;
import java.io.*;

public class CountDirectoryScans {
    public static boolean TRACK_LOCATIONS = false;
    public static Map<File, Integer> COUNTS = new HashMap<File, Integer>();
    public static Map<File, List<Exception>> LOCATIONS = new HashMap<File, List<Exception>>();

    @Advice.OnMethodEnter
    public synchronized static void interceptVisitFrom(@Advice.Argument(1) File fileOrDirectory) {
        File key = fileOrDirectory.getAbsoluteFile();
        Integer count = COUNTS.get(key);
        COUNTS.put(key, count != null ? count+1 : 1);

        if(TRACK_LOCATIONS) {
            List<Exception> locations = LOCATIONS.get(key);
            if(locations == null) {
               locations = new ArrayList<Exception>();
               LOCATIONS.put(key, locations);
            }
            locations.add(new Exception());
        }
    }

    public synchronized static void reset() {
        COUNTS.clear();
        LOCATIONS.clear();
    }
}
'''

        buildFile << '''
gradle.advice.DirectoryScanningInterceptor.install()
gradle.buildFinished {
   def countDirectoryScans = Gradle.class.getClassLoader().loadClass("gradle.advice.CountDirectoryScans")
   // serialize counts to file
   file('countDirectoryScans.ser').withOutputStream {
      new ObjectOutputStream(it).writeObject(countDirectoryScans.COUNTS)
   }
   if (countDirectoryScans.TRACK_LOCATIONS) {
       // serialize locations to file
       file('directoryScanLocations.ser').withOutputStream {
          new ObjectOutputStream(it).writeObject(countDirectoryScans.LOCATIONS)
       }
   }
   countDirectoryScans.reset()
}
'''
    }

    // read serialized counts from file and transform paths to relative paths
    private Map<String, Integer> loadDirectoryScanCounts() {
        Map<File, Integer> scanCounts
        file('countDirectoryScans.ser').withInputStream {
            scanCounts = new ObjectInputStream(it).readObject()
        }
        // transform into relative paths
        scanCounts.collectEntries { File dir, Integer count ->
            [GFileUtils.relativePath(testDirectory, dir), count]
        }
    }

    private Map<File, List<Exception>> loadDirectoryScanLocations() {
        def file = file('directoryScanLocations.ser')
        if (file.exists()) {
            return file.withInputStream {
                new ObjectInputStream(it).readObject()
            }
        }
        null
    }

    private void printDirectoryScanLocations() {
        def locations = loadDirectoryScanLocations()
        locations?.each { k, v ->
            println "directory: ${GFileUtils.relativePath(testDirectory, k)}"
            v.each { Exception e ->
                println "Scan originated from:"
                printStackTrace(e, System.out)
                println()
            }
            println "-----------------------------------------"
        }
    }

    private void printStackTrace(Throwable exception, Appendable builder) {
        for (StackTraceElement element : exception.getStackTrace()) {
            if (element.getLineNumber() > 0) {
                builder.append(" at ")
                builder.append(element.getClassName())
                builder.append('(')
                builder.append(element.getFileName())
                builder.append(':')
                builder.append(String.valueOf(element.getLineNumber()))
                builder.append(')')
                builder.append('\n')
            }
            if (element.className == 'org.gradle.execution.DefaultBuildExecuter') {
                break
            }
        }
        builder.append('\n')
    }

    def "count directory scans for Java project with single source file"() {
        given:
        buildFile << '''
apply plugin: 'java'

//Gradle.class.getClassLoader().loadClass("gradle.advice.CountDirectoryScans").TRACK_LOCATIONS = true
'''
        file('src/main/java/Test.java') << 'class Test {}'
        expect:
        succeeds("build")
        def scanCounts = loadDirectoryScanCounts()
        //println scanCounts.sort { a, b -> a.value.compareTo(b.value) }
        //printDirectoryScanLocations()
        scanCounts.each { String path, Integer count ->
            assert count <= 3 : "$path has too many scans."
        }
    }
}
