/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.vcs.fixtures;

import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class TemporaryGitRepository extends RepositoryTestCase implements TestRule {
    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                setUp();
                base.evaluate();
                tearDown();
            }
        };
    }

    public static void checkFile(File f, String checkData) throws IOException {
        RepositoryTestCase.checkFile(f, checkData);
    }

    public static void copyFile(final File src, final File dst)
        throws IOException {
        RepositoryTestCase.copyFile(src, dst);
    }

    public FileRepository getDatabase() {
        return db;
    }

    public File getTrash() {
        return trash;
    }

    @Override
    public File writeTrashFile(String name, String data) throws IOException {
        return super.writeTrashFile(name, data);
    }

    @Override
    protected Path writeLink(String link, String target) throws Exception {
        return super.writeLink(link, target);
    }

    @Override
    public File writeTrashFile(String subdir, String name, String data) throws IOException {
        return super.writeTrashFile(subdir, name, data);
    }

    @Override
    public String read(String name) throws IOException {
        return super.read(name);
    }

    @Override
    public boolean check(String name) {
        return super.check(name);
    }

    @Override
    public void deleteTrashFile(String name) throws IOException {
        super.deleteTrashFile(name);
    }

    @Override
    public void checkoutBranch(String branchName)
        throws IllegalStateException, IOException {
        super.checkoutBranch(branchName);
    }

    @Override
    public File writeTrashFiles(boolean ensureDistinctTimestamps, String... contents) throws IOException, InterruptedException {
        return super.writeTrashFiles(ensureDistinctTimestamps, contents);
    }

    @Override
    public RevCommit commitFile(String filename, String contents, String branch) {
        return super.commitFile(filename, contents, branch);
    }

    @Override
    public DirCacheEntry createEntry(String path, FileMode mode) {
        return super.createEntry(path, mode);
    }

    @Override
    public DirCacheEntry createEntry(String path, FileMode mode, String content) {
        return super.createEntry(path, mode, content);
    }

    @Override
    public DirCacheEntry createEntry(String path, FileMode mode, int stage, String content) {
        return super.createEntry(path, mode, stage, content);
    }

    @Override
    public void resetIndex(FileTreeIterator treeItr) throws FileNotFoundException, IOException {
        super.resetIndex(treeItr);
    }

    @Override
    public void createBranch(ObjectId objectId, String branchName) throws IOException {
        super.createBranch(objectId, branchName);
    }

    @Override
    public File getTemporaryDirectory() {
        return super.getTemporaryDirectory();
    }

    @Override
    public List<File> getCeilings() {
        return super.getCeilings();
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public void recursiveDelete(File dir) {
        super.recursiveDelete(dir);
    }

    @Override
    public FileRepository createBareRepository() throws IOException {
        return super.createBareRepository();
    }

    @Override
    public FileRepository createWorkRepository() throws IOException {
        return super.createWorkRepository();
    }

    @Override
    public File createTempDirectory(String name) throws IOException {
        return super.createTempDirectory(name);
    }

    @Override
    public File createUniqueTestGitDir(boolean bare) throws IOException {
        return super.createUniqueTestGitDir(bare);
    }

    @Override
    public File createTempFile() throws IOException {
        return super.createTempFile();
    }

    @Override
    public int runHook(Repository db, File hook, String... args) throws IOException, InterruptedException {
        return super.runHook(db, hook, args);
    }

    @Override
    public File write(String body) throws IOException {
        return super.write(body);
    }

    @Override
    public void write(File f, String body) throws IOException {
        super.write(f, body);
    }

    @Override
    public String read(File f) throws IOException {
        return super.read(f);
    }
}
