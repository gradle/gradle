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

package org.gradle.smoketests

import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.GradleRunner

class AnnotationProcessingSmokeTest extends AbstractSmokeTest {

    def 'project lombok works when options.fork=#fork'() {
        given:
        buildFile << """
            plugins {
                id("java")
            }
            ${mavenCentralRepository()}
            dependencies {
                compileOnly 'org.projectlombok:lombok:1.18.18'
                annotationProcessor 'org.projectlombok:lombok:1.18.18'
            }
            compileJava.options.fork = $fork
        """
        file("src/main/java/ValExample.java") << """
            import java.util.ArrayList;
            import java.util.HashMap;
            import lombok.val;

            public class ValExample {
              public String example() {
                val example = new ArrayList<String>();
                example.add("Hello, World!");
                val foo = example.get(0);
                return foo.toLowerCase();
              }

              public void example2() {
                val map = new HashMap<Integer, String>();
                map.put(0, "zero");
                map.put(5, "five");
                for (val entry : map.entrySet()) {
                  System.out.printf("%d: %s\\n", entry.getKey(), entry.getValue());
                }
              }
            }
        """
        GradleRunner gradleRunner = runner("compileJava")
        if (JavaVersion.current().isJava9Compatible()) {
            gradleRunner.withArguments("-Dorg.gradle.jvmargs=--add-opens jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED")
        }

        expect:
        gradleRunner.build()

        where:
        fork << [true, false]
    }
}
