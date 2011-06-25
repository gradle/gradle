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
package org.gradle.integtests.fixtures;

import org.gradle.util.TestFile;

import java.io.File;
import java.util.*;

public class DaemonGradleExecuter extends ForkingGradleExecuter {
    private static final Set<File> DAEMONS = new HashSet<File>();

    public DaemonGradleExecuter(TestFile gradleHomeDir) {
        super(gradleHomeDir);
    }

    @Override
    protected Map doRun(boolean expectFailure) {
        registerDaemon(getUserHomeDir());
        Map result = super.doRun(expectFailure);
        String output = (String) result.get("output");
        output = output.replace(String.format("Note: the Gradle build daemon is an experimental feature.%n"), "");
        output = output.replace(String.format("As such, you may experience unexpected build failures. You may need to occasionally stop the daemon.%n"), "");
        result.put("output", output);
        return result;
    }

    public static void registerDaemon(final File userHomeDir) {
        assert userHomeDir != null;
        if (!DAEMONS.add(userHomeDir)) {
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
//                ExecHandleBuilder builder = new ExecHandleBuilder();
//                builder.workingDir(new File(".").getAbsolutePath());
//                builder.executable(Jvm.current().getJpsExecutable());
//                builder.args("-lm");
//                builder.setStandardOutput(new ByteArrayOutputStream());
//                builder.build().start().waitForFinish();
            }
        }));
    }

    @Override
    protected List<String> getAllArgs() {
        List<String> args = new ArrayList<String>();
        args.add("--daemon");
        args.addAll(super.getAllArgs());
        return args;
    }
}
