/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.process.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.IgnoreIf
import spock.lang.Issue

@Issue('https://github.com/gradle/gradle/issues/6114')
@Requires(TestPrecondition.WINDOWS)
@IntegrationTestTimeout(60)
class WindowsSubprocessHangIntegrationTest extends AbstractIntegrationSpec {
    @IgnoreIf({ AvailableJavaHomes.jdk6 == null })
    def 'can handle subprocess stream on Windows'() {
        when:
        buildFile << """
            import org.gradle.process.internal.ExecHandleFactory
import org.gradle.internal.classloader.ClasspathUtil

class ParentProcess {
   static String JAVA6 = '${AvailableJavaHomes.jdk6.javaExecutable.absolutePath.replace('\\', '/')}'
   public static void main(String[] args) throws Exception {
       ProcessBuilder builder = new ProcessBuilder(JAVA6, '-cp', System.getProperty('java.class.path'), ChildProcess.class.name);
       builder.start();
   }
}

public class ChildProcess {
   public static void main(String[] args) throws Exception {
       Thread.sleep(3600*1000);
   }
}

task hang {
   doLast {
       String classpath = ClasspathUtil.getClasspath(ParentProcess.class.classLoader)
               .asFiles
               .collect { it.absolutePath}
               .join(';')

       def execHandler = project.services.get(ExecHandleFactory).newExec()
               .executable(ParentProcess.JAVA6)
               .args('-cp', classpath, ParentProcess.class.name)
               .build()

       execHandler.start()

       Thread.sleep(2000)
       
       execHandler.abort()
   }
}
        """

        then:
        succeeds('hang')
    }
}
