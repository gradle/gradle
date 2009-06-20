/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.integtests;

import org.junit.Assert;
import org.junit.Test;
import org.junit.Ignore;

public class BuildScriptClasspathIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void providesADefaultBuildForBuildSrcProject() {
        testFile("buildSrc/src/main/java/BuildClass.java").writelns("public class BuildClass { }");
        testFile("build.gradle").writelns("new BuildClass()");
        inTestDirectory().withTaskList().run();
    }

    @Test
    public void canDeclareClasspathInBuildScript() {
        ArtifactBuilder builder = artifactBuilder();
        builder.sourceFile("org/gradle/test/BuildClass.java").writelns(
                "package org.gradle.test;",
                "public class BuildClass { }"
        );
        builder.sourceFile("org/gradle/test2/AnotherBuildClass.java").writelns(
                "package org.gradle.test2;",
                "public class AnotherBuildClass { }"
        );
        builder.buildJar(testFile("repo/test-1.3.jar").asFile());

        testFile("build.gradle").writelns(
                "import org.gradle.test.BuildClass",
                "import org.gradle.test2.*",
                "scriptclasspath {",
                "  repositories {",
                "    flatDir dirs: file('repo')",
                "  }",
                "  dependencies {",
                "    classpath name: 'test', version: '1.+'",
                "  }",
                "}",
                "task hello << {",
                "  new org.gradle.test.BuildClass()",
                "  new BuildClass()",
                "  new AnotherBuildClass()",
                "}",
                "a = new BuildClass()",
                "b = AnotherBuildClass",
                "class TestClass extends BuildClass { }",
                "def aMethod() { return new AnotherBuildClass() }"
        );
        inTestDirectory().withTasks("hello").run();
    }

    @Test @Ignore
    public void collectsStdoutDuringClasspathDeclaration() {
        Assert.fail("implement me");
    }

    @Test @Ignore
    public void reportsFailureDuringClasspathDeclaration() {
        Assert.fail("implement me");
    }
    
    @Test @Ignore
    public void canUseImportedClassesInClasspathDeclaration() {
        Assert.fail("implement me");
    }

    @Test @Ignore
    public void inheritsClassPathOfParentProject() {
        Assert.fail("implement me");
    }

    @Test @Ignore
    public void canUseBuildSrcClassesInClasspathDeclaration() {
        Assert.fail("implement me");
    }

    @Test @Ignore
    public void canInjectClassPathIntoSubProjects() {
        Assert.fail("implement me");
    }
    
    @Test @Ignore
    public void canReuseClassPathRepositories() {
        Assert.fail("implement me");
    }
}
