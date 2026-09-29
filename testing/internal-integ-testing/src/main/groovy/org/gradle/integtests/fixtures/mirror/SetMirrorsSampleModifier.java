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

package org.gradle.integtests.fixtures.mirror;

import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.integtests.fixtures.RepoScriptBlockUtil;
import org.gradle.exemplar.model.Command;
import org.gradle.exemplar.model.Sample;
import org.gradle.exemplar.test.runner.SampleModifier;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.gradle.api.internal.artifacts.BaseRepositoryFactory.PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY;
import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.gradlePluginRepositoryMirrorUrl;
import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.isMirrorEnabled;

public class SetMirrorsSampleModifier implements SampleModifier {

    private static final Pattern OWN_INIT_SCRIPT = Pattern.compile("(^|\\s)(--init-script|-I)(\\s|=|$)");

    private final File initScript = RepoScriptBlockUtil.createMirrorInitScript();
    private File mavenHomeWithCentralMirror;

    @Override
    public Sample modify(Sample sample) {
        if (sample.getId().contains("usePluginsInInitScripts") || !isMirrorEnabled()) {
            // usePluginsInInitScripts asserts using https://repo.gradle.org/gradle/repo
            return sample;
        }
        List<Command> commands = sample.getCommands();
        List<Command> modifiedCommands = new ArrayList<Command>();
        for (Command command : commands) {
            if ("gradle".equals(command.getExecutable())) {
                List<String> args = new ArrayList<String>(command.getArgs());
                args.add("--init-script");
                args.add(initScript.getAbsolutePath());
                args.add("-D" + PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY + "=" + gradlePluginRepositoryMirrorUrl());
                List<String> flags = new ArrayList<String>(command.getFlags());
                if (usesOwnInitScript(command) && isMavenCentralMirrored()) {
                    flags.add("-Dorg.gradle.mirror.maven.settings=true");
                    flags.add("-Dorg.gradle.sampletest.env.M2_HOME=" + mavenHomeWithCentralMirror().getAbsolutePath());
                }
                modifiedCommands.add(command.toBuilder().setArgs(args).setFlags(flags).build());
            } else {
                modifiedCommands.add(command);
            }
        }
        return new Sample(sample.getId(), sample.getProjectDir(), modifiedCommands);
    }

    private static boolean usesOwnInitScript(Command command) {
        return OWN_INIT_SCRIPT.matcher(String.join(" ", command.getArgs()) + " " + String.join(" ", command.getFlags())).find();
    }

    private static boolean isMavenCentralMirrored() {
        return !RepoScriptBlockUtil.getMavenCentralMirrorUrl().equals(ArtifactRepositoryContainer.MAVEN_CENTRAL_URL);
    }

    private synchronized File mavenHomeWithCentralMirror() {
        if (mavenHomeWithCentralMirror == null) {
            try {
                File mavenHome = Files.createTempDirectory("maven-home").toFile();
                mavenHome.deleteOnExit();
                File settings = new File(mavenHome, "conf/settings.xml");
                Files.createDirectories(settings.getParentFile().toPath());
                Files.write(settings.toPath(), ("<settings>\n"
                    + "    <mirrors>\n"
                    + "        <mirror>\n"
                    + "            <id>central-mirror</id>\n"
                    + "            <mirrorOf>central</mirrorOf>\n"
                    + "            <url>" + RepoScriptBlockUtil.getMavenCentralMirrorUrl() + "</url>\n"
                    + "        </mirror>\n"
                    + "    </mirrors>\n"
                    + "</settings>\n").getBytes(StandardCharsets.UTF_8));
                mavenHomeWithCentralMirror = mavenHome;
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
        return mavenHomeWithCentralMirror;
    }
}
