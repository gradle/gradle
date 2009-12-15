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

import org.gradle.CacheUsage;
import org.gradle.StartParameter;
import org.gradle.util.Resources;
import org.gradle.util.TemporaryFolder;
import org.junit.Rule;

import java.io.File;

public class AbstractIntegrationTest {
    @Rule public TemporaryFolder testDir = new TemporaryFolder();
    @Rule public Resources resources = new Resources();

    public TestFile getTestDir() {
        return testDir.getDir();
    }

    public TestFile testFile(String name) {
        return getTestDir().file(name);
    }

    public TestFile testFile(File dir, String name) {
        return new TestFile(dir, name);
    }

    protected File getTestBuildFile(String name) {
        TestFile sourceFile = resources.getResource("testProjects/" + name);
        TestFile destFile = testFile(sourceFile.getName());
        sourceFile.copyTo(destFile);
        return destFile;
    }

    private StartParameter startParameter() {
        StartParameter parameter = new StartParameter();
        parameter.setGradleHomeDir(testFile("gradle-home"));

        //todo - this should use the src/toplevel/gradle-imports file
        testFile("gradle-home/gradle-imports").writelns("import static org.junit.Assert.*",
                "import static org.hamcrest.Matchers.*",
                "import org.gradle.*",
                "import org.gradle.api.*",
                "import org.gradle.api.invocation.*",
                "import org.gradle.api.file.*",
                "import org.gradle.api.logging.*",
                "import org.gradle.api.tasks.*",
                "import org.gradle.api.tasks.bundling.*");

        testFile("gradle-home/plugin.properties").writelns("java=org.gradle.api.plugins.JavaPlugin",
                "groovy=org.gradle.api.plugins.GroovyPlugin",
                "scala=org.gradle.api.plugins.scala.ScalaPlugin",
                "war=org.gradle.api.plugins.WarPlugin",
                "maven=org.gradle.api.plugins.MavenPlugin",
                "code-quality=org.gradle.api.plugins.quality.CodeQualityPlugin",
                "base=org.gradle.api.plugins.BasePlugin");

        parameter.setGradleUserHomeDir(getUserHomeDir());

        parameter.setSearchUpwards(false);
        parameter.setCacheUsage(CacheUsage.ON);
        parameter.setCurrentDir(getTestDir());

        return parameter;
    }

    private static TestFile getUserHomeDir() {
        String path = System.getProperty("integTest.gradleUserHomeDir", "intTestHomeDir");
        return new TestFile(new File(path));
    }

    protected GradleExecuter inTestDirectory() {
        return inDirectory(getTestDir());
    }

    protected GradleExecuter inDirectory(File directory) {
        return new InProcessGradleExecuter(startParameter()).inDirectory(directory);
    }

    protected GradleExecuter usingBuildFile(File file) {
        StartParameter parameter = startParameter();
        parameter.setBuildFile(file);
        return new InProcessGradleExecuter(parameter);
    }

    protected GradleExecuter usingBuildScript(String script) {
        return new InProcessGradleExecuter(startParameter()).usingBuildScript(script);
    }

    protected GradleExecuter usingProjectDir(File projectDir) {
        StartParameter parameter = startParameter();
        parameter.setProjectDir(projectDir);
        return new InProcessGradleExecuter(parameter);
    }

    protected ArtifactBuilder artifactBuilder() {
        return new GradleBackedArtifactBuilder(new InProcessGradleExecuter(startParameter()), getTestDir().file("artifacts"));
    }
}
