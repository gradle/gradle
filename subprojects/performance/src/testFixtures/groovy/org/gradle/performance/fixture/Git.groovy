/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.performance.fixture

import org.gradle.process.internal.ExecHandleBuilder

class Git {
    private static Git git
    final String commitId
    final String branchName

    public static Git current() {
        if (git == null) {
            git = new Git()
        }
        return git
    }

    private Git() {
        commitId = determineCommitId()
        branchName = determineBranchName()
    }

    private static String determineCommitId() {
        def output = new ByteArrayOutputStream()
        def builder = new ExecHandleBuilder()
        builder.workingDir = new File(".").absoluteFile
        builder.commandLine = ["git", "log", "-1", "--format=%H"]
        builder.standardOutput = output
        builder.build().start().waitForFinish().assertNormalExitValue()
        return new String(output.toByteArray())
    }

    private static String determineBranchName() {
        def output = new ByteArrayOutputStream()
        def builder = new ExecHandleBuilder()
        builder.workingDir = new File(".").absoluteFile
        builder.commandLine = ["git", "symbolic-ref", "--short", "HEAD"]
        builder.standardOutput = output
        builder.build().start().waitForFinish().assertNormalExitValue()
        return new String(output.toByteArray())
    }
}
