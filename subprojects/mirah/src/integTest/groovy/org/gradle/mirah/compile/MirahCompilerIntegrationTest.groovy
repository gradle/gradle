/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.mirah.compile

import org.gradle.integtests.fixtures.MirahCoverage
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

@TargetCoverage({MirahCoverage.DEFAULT})
class MirahCompilerIntegrationTest extends BasicMirahCompilerIntegrationTest {
    @Rule TestResources testResources = new TestResources(temporaryFolder)

    String compilerConfiguration() {
        """
compileMirah.mirahCompileOptions.with {
}
        """
    }

    String logStatement() {
        "Compiling with Zinc Mirah compiler"
    }

    def compilesMirahCodeIncrementally() {
        setup:
        def person = file("build/classes/main/Person.class")
        def house = file("build/classes/main/House.class")
        def other = file("build/classes/main/Other.class")
        run("compileMirah")

        when:
        file("src/main/mirah/Person.mirah").delete()
        file("src/main/mirah/Person.mirah") << "class Person; end"
        args("-i", "-PmirahVersion=$version") // each run clears args (argh!)
        run("compileMirah")

        then:
        person.exists()
        house.exists()
        other.exists()
        person.lastModified() != old(person.lastModified())
        house.lastModified() != old(house.lastModified())
        // other.lastModified() == old(other.lastModified()) // currently, the mirah compiler rewrites all classes, even those which compile to an identical .class file
    }

    def compilesJavaCodeIncrementally() {
        setup:
        def person = file("build/classes/main/Person.class")
        def house = file("build/classes/main/House.class")
        def other = file("build/classes/main/Other.class")
        run("compileMirah")

        when:
        file("src/main/mirah/Person.java").delete()
        file("src/main/mirah/Person.java") << "public class Person {}"
        args("-i", "-PmirahVersion=$version") // each run clears args (argh!)
        run("compileMirah")

        then:
        person.lastModified() != old(person.lastModified())
        house.lastModified() != old(house.lastModified())
        other.lastModified() == old(other.lastModified())
    }

    def compilesIncrementallyAcrossProjectBoundaries() {
        setup:
        def person = file("prj1/build/classes/main/Person.class")
        def house = file("prj2/build/classes/main/House.class")
        def other = file("prj2/build/classes/main/Other.class")
        run("compileMirah")

        when:
        file("prj1/src/main/mirah/Person.mirah").delete()
        file("prj1/src/main/mirah/Person.mirah") << "class Person"
        args("-i", "-PmirahVersion=$version") // each run clears args (argh!)
        run("compileMirah")

        then:
        person.lastModified() != old(person.lastModified())
        house.lastModified() != old(house.lastModified())
        other.lastModified() == old(other.lastModified())
    }
}