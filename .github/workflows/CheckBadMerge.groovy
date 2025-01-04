/*
 * Copyright 2023 the original author or authors.
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

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.nio.file.Files
import java.nio.file.Paths

/**
 * See https://github.com/gradle/gradle-private/issues/3919
 *
 * When merging `releaseX` branch into `master`, we should only use the release note from the `master` branch,
 * but sometimes changes on release notes.md was brought to master and merged unnoticed,
 * e.g https://github.com/gradle/gradle/pull/25825
 *
 * This script is to check if there is any merge commit that brings changes from release branch to master.
 * Usage: groovy CheckBadMerge.groovy <commit1> <commit2> ...
 * If any "bad" merge commit is found, it will print the details and exit with non-zero code.
 */
class CheckBadMerge {
    private static final THREAD_POOL = Executors.newCachedThreadPool()

    private static final List<String> MONITORED_FILES = [
        "subprojects/docs/src/docs/release/notes.md",
        "platforms/documentation/docs/src/docs/release/notes.md",
        "subprojects/launcher/src/main/resources/release-features.txt",
        "platforms/core-runtime/launcher/src/main/resources/release-features.txt"
    ]

    static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: groovy CheckBadMerge.groovy <commits_file>")
            System.exit(1)
        }

        List<String> commits = Files.readAllLines(Paths.get(args[0]))
        println("Commits to check: ${Arrays.toString(commits)}")
        try {
            commits.each { checkCommit(it) }
        } finally {
            THREAD_POOL.shutdown()
        }
    }

    static void checkCommit(String commit) {
        List<String> parentCommits = parentCommitsOf(commit)
        if (parentCommits.size() != 2) {
            println("$commit is not a merge commit we're looking for. Parents: $parentCommits")
            return
        }

        // The correct state we are looking for is:
        // 1. It's a merge commit.
        // 2. One of its parent commits is from master only.
        // 3. Another parent commit is from master and release branch.
        // Otherwise, skip this commit.
        List<String> p1Branches = branchesOf(parentCommits[0])
        List<String> p2Branches = branchesOf(parentCommits[1])

        if (p1Branches.contains("origin/master") && !p2Branches.contains("origin/master") && p2Branches.any { it.startsWith("origin/release") }) {
            List<String> badFiles = MONITORED_FILES.grep { isBadFileInMergeCommit(it, commit, parentCommits[0], parentCommits[1]) }
            if (!badFiles.isEmpty()) {
                throw new RuntimeException("Found bad files in merge commit $commit: $badFiles")
            } else {
                println("No bad files found in $commit")
            }
        } else {
            println("$commit is not a merge commit we're looking for. Parents: $parentCommits, p1Branches: $p1Branches, p2Branches: $p2Branches")
        }
    }

    /**
     * Check if the given file is "bad": we should only use the release note from the master branch.
     * This means that every line in the merge commit version should be either:
     * - Only exists on `master`.
     * - Exists on `master` and `releaseX`.
     * If any line is only present on `releaseX` version, then it's a bad file.
     * Also, we ignore empty lines.
     */
    static boolean isBadFileInMergeCommit(String filePath, String mergeCommit, String masterCommit, String releaseCommit) {
        try {
            List<String> mergeCommitFileLines = showFileOnCommit(mergeCommit, filePath).readLines()
            List<String> masterCommitFileLines = showFileOnCommit(masterCommit, filePath).readLines()
            List<String> releaseCommitFileLines = showFileOnCommit(releaseCommit, filePath).readLines()
            for (String line in mergeCommitFileLines) {
                if (line.trim().isEmpty()) {
                    continue
                }
                if (!masterCommitFileLines.contains(line) && releaseCommitFileLines.contains(line)) {
                    println("Found bad file $filePath in merge commit $mergeCommit: '$line' only exists in $releaseCommit but not in $masterCommit")
                    return true
                }
            }
        } catch (AbortException ignore) {
            return false
        }
        return false
    }

    static class AbortException extends RuntimeException {
    }

    static String showFileOnCommit(String commit, String filePath) {
        ExecResult execResult = exec("git show $commit:$filePath")
        if (execResult.returnCode != 0 && execResult.stderr ==~ /path '.*' exists on disk, but not in '.*'/) {
            println("File $filePath does not exist on commit $commit, skip.")
            throw new AbortException()
        }
        return execResult.stdout
    }

    static List<String> branchesOf(String commit) {
        return getStdout("git branch -r --contains $commit")
            .readLines()
            .collect { it.replace("*", "") } // remove the * from the current branch, e.g. * master -> master
            .collect { it.trim() }
            .grep { !it.isEmpty() }
    }

    static List<String> parentCommitsOf(String commit) {
        return getStdout("git show --format=%P --no-patch $commit")
            .split(" ").collect { it.trim() }.grep { !it.isEmpty() }
    }

    @groovy.transform.ToString
    static class ExecResult {
        String stdout
        String stderr
        int returnCode
    }

    static ExecResult exec(String command) {
        Process process = command.execute()
        def stdoutFuture = readStreamAsync(process.inputStream)
        def stderrFuture = readStreamAsync(process.errorStream)

        int returnCode = process.waitFor()
        String stdout = stdoutFuture.get()
        String stderr = stderrFuture.get()
        return new ExecResult(stderr: stderr, stdout: stdout, returnCode: returnCode)
    }

    static String getStdout(String command) {
        ExecResult execResult = exec(command)

        assert execResult.returnCode == 0: "$command failed with return code: $execResult"
        return execResult.stdout
    }

    static Future<String> readStreamAsync(InputStream inputStream) {
        return THREAD_POOL.submit({ inputStream.text } as Callable) as Future<String>
    }
}
