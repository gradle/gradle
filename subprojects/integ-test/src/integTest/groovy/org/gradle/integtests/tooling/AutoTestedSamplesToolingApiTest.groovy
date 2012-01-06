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

package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.AutoTestedSamplesUtil
import org.gradle.util.Jvm
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 1/5/12
 */
@IgnoreIf({!Jvm.current().java6Compatible})
public class AutoTestedSamplesToolingApiTest extends Specification {

    @Rule public final TemporaryFolder temp = new TemporaryFolder()

    @Ignore
    void runSamples() {
        expect:

        def util = new AutoTestedSamplesUtil()
        util.findSamples("subprojects/tooling-api/src/main") { file, sample ->
            println "Found sample: ${sample.split("\n")[0]} (...) in $file"
            def javaSource = """
//some typical imports
import org.gradle.tooling.*;
import org.gradle.tooling.model.*;
import org.gradle.tooling.model.build.*;
import java.io.*;

public class Sample {
  public static void main(String ... args) {
    $sample
  }
}
"""
            tryCompile(javaSource)
        }
    }

    void tryCompile(final String source) {}
}