/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.tasks.javadoc.internal;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.external.javadoc.internal.JavadocExecHandleBuilder;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.process.internal.ExecException;
import org.gradle.util.GFileUtils;

public class JavadocGenerator implements Compiler<JavadocSpec> {

    private final static Logger LOG = Logging.getLogger(JavadocGenerator.class);

    private final ExecActionFactory execActionFactory;

    public JavadocGenerator(ExecActionFactory execActionFactory) {
        this.execActionFactory = execActionFactory;
    }

    @Override
    public WorkResult execute(JavadocSpec spec) {
        JavadocExecHandleBuilder javadocExecHandleBuilder = new JavadocExecHandleBuilder(execActionFactory);
        javadocExecHandleBuilder.setExecutable(spec.getExecutable());
        javadocExecHandleBuilder.execDirectory(spec.getWorkingDir()).options(spec.getOptions()).optionsFile(spec.getOptionsFile());

        ExecAction execAction = javadocExecHandleBuilder.getExecHandle();
        if (spec.isIgnoreFailures()) {
            execAction.setIgnoreExitValue(true);
        }

        try {
            execAction.execute();
        } catch (ExecException e) {
            LOG.info("Problems generating Javadoc."
                    + "\n  Command line issued: " + execAction.getCommandLine()
                    + "\n  Generated Javadoc options file has following contents:\n------\n{}------", GFileUtils.readFileQuietly(spec.getOptionsFile()));
            throw new GradleException(String.format("Javadoc generation failed. Generated Javadoc options file (useful for troubleshooting): '%s'", spec.getOptionsFile()), e);
        }

        return WorkResults.didWork(true);
    }
}
