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

package org.gradle.test.fixtures.file;

import com.google.common.collect.Lists;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Zip;
import org.codehaus.groovy.runtime.ResourceGroovyMethods;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashing;
import org.gradle.testing.internal.util.RetryUtil;
import org.hamcrest.Matcher;
import org.intellij.lang.annotations.Language;
import org.junit.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class TestFile extends File {
    private boolean useNativeTools;
    private final File relativeBase;

    public TestFile(File file, Object... path) {
        super(join(file, path).getAbsolutePath());
        this.relativeBase = file;
    }

    public TestFile(URI uri) {
        this(new File(uri));
    }

    public TestFile(String path) {
        this(new File(path));
    }

    public TestFile(URL url) {
        this(toUri(url));
    }

    public TestFile usingNativeTools() {
        useNativeTools = true;
        return this;
    }

    public TestFile java(@Language("java") String src) {
        Assert.assertTrue(getName() + " doesn't look like a Java file.", getName().endsWith(".java"));
        return setText(src);
    }

    Object writeReplace() throws ObjectStreamException {
        return new File(getAbsolutePath());
    }

    @Override
    public File getCanonicalFile() throws IOException {
        return new File(getAbsolutePath()).getCanonicalFile();
    }

    @Override
    public String getCanonicalPath() throws IOException {
        return new File(getAbsolutePath()).getCanonicalPath();
    }

    private static URI toUri(URL url) {
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static File join(File file, Object[] path) {
        File current = file.getAbsoluteFile();
        for (Object p : path) {
            current = new File(current, p.toString());
        }
        try {
            return current.getCanonicalFile();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not canonicalise '%s'.", current), e);
        }
    }

    public TestFile file(Object... path) {
        try {
            return new TestFile(this, path);
        } catch (RuntimeException e) {
            throw new RuntimeException(String.format("Could not locate file '%s' relative to '%s'.", Arrays.toString(path), this), e);
        }
    }

    public List<TestFile> files(Object... paths) {
        List<TestFile> files = new ArrayList<>();
        for (Object path : paths) {
            files.add(file(path));
        }
        return files;
    }

    public TestFile withExtension(String extension) {
        return getParentFile().file(org.gradle.internal.FileUtils.withExtension(getName(), extension));
    }

    public TestFile writelns(String... lines) {
        return writelns(Arrays.asList(lines));
    }

    public TestFile write(Object content) {
        try {
            FileUtils.writeStringToFile(this, content.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not write to test file '%s'", this), e);
        }
        return this;
    }

    public TestFile leftShift(Object content) {
        getParentFile().mkdirs();
        try {
            ResourceGroovyMethods.leftShift(this, content);
            return this;
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not append to test file '%s'", this), e);
        }
    }

    public TestFile setText(String content) {
        getParentFile().mkdirs();
        try {
            ResourceGroovyMethods.setText(this, content);
            return this;
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not append to test file '%s'", this), e);
        }
    }

    @Override
    public TestFile[] listFiles() {
        File[] children = super.listFiles();
        if (children == null) {
            return null;
        }
        TestFile[] files = new TestFile[children.length];
        for (int i = 0; i < children.length; i++) {
            File child = children[i];
            files[i] = new TestFile(child);
        }
        return files;
    }

    public String getText() {
        assertIsFile();
        try {
            return FileUtils.readFileToString(this, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not read from test file '%s'", this), e);
        }
    }

    public Map<String, String> getProperties() {
        assertIsFile();
        Properties properties = new Properties();
        try {
            try (FileInputStream inStream = new FileInputStream(this)) {
                properties.load(inStream);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Map<String, String> map = new HashMap<>();
        for (Object key : properties.keySet()) {
            map.put(key.toString(), properties.getProperty(key.toString()));
        }
        return map;
    }

    public Manifest getManifest() {
        assertIsFile();
        try {
            try (JarFile jarFile = new JarFile(this)) {
                return jarFile.getManifest();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> linesThat(Matcher<? super String> matcher) {
        try {
            try (BufferedReader reader = new BufferedReader(new FileReader(this))) {
                List<String> lines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (matcher.matches(line)) {
                        lines.add(line);
                    }
                }
                return lines;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void unzipTo(File target) {
        assertIsFile();
        new TestFileHelper(this).unzipTo(target, useNativeTools);
    }

    public void untarTo(File target) {
        assertIsFile();

        new TestFileHelper(this).untarTo(target, useNativeTools);
    }

    public void copyTo(File target) {
        if (isDirectory()) {
            try {
                final Path targetDir = target.toPath();
                final Path sourceDir = this.toPath();
                Files.walkFileTree(sourceDir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path sourceFile, BasicFileAttributes attributes) throws IOException {
                        Path targetFile = targetDir.resolve(sourceDir.relativize(sourceFile));
                        Files.copy(sourceFile, targetFile, COPY_ATTRIBUTES, REPLACE_EXISTING);

                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
                        Path newDir = targetDir.resolve(sourceDir.relativize(dir));
                        Files.createDirectories(newDir);

                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(String.format("Could not copy test directory '%s' to '%s'", this, target), e);
            }
        } else {
            try {
                FileUtils.copyFile(this, target);
            } catch (IOException e) {
                throw new RuntimeException(String.format("Could not copy test file '%s' to '%s'", this, target), e);
            }
        }
    }

    public void copyFrom(File target) {
        new TestFile(target).copyTo(this);
    }

    public void copyFrom(final URL resource) {
        final TestFile testFile = this;
        RetryUtil.retry(new Closure(null, null) {
            @SuppressWarnings("UnusedDeclaration")
            void doCall() {
                try {
                    FileUtils.copyURLToFile(resource, testFile);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
    }

    public void moveToDirectory(File target) {
        if (target.exists() && !target.isDirectory()) {
            throw new RuntimeException(String.format("Target '%s' is not a directory", target));
        }
        try {
            FileUtils.moveFileToDirectory(this, target, true);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not move test file '%s' to directory '%s'", this, target), e);
        }
    }

    public TestFile touch() {
        try {
            FileUtils.touch(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertIsFile();
        return this;
    }

    /**
     * Changes the last modified time for this file so that it is different to and smaller than its current last modified time, within file system resolution.
     */
    public TestFile makeOlder() {
        makeOlder(this);
        return this;
    }

    /**
     * Changes the last modified time for the given file so that it is different to and smaller than its current last modified time, within file system resolution.
     */
    public static void makeOlder(File file) {
        // Just move back 2 seconds
        assert file.setLastModified(file.lastModified() - 2000L);
    }

    /**
     * Creates a directory structure specified by the given closure.
     * <pre>
     * dir.create {
     *     subdir1 {
     *        file 'somefile.txt'
     *     }
     *     subdir2 { nested { file 'someFile' } }
     * }
     * </pre>
     */
    public TestFile create(@DelegatesTo(value = TestWorkspaceBuilder.class, strategy = Closure.DELEGATE_FIRST) Closure structure) {
        assertTrue(isDirectory() || mkdirs());
        new TestWorkspaceBuilder(this).apply(structure);
        return this;
    }

    @Override
    public TestFile getParentFile() {
        return super.getParentFile() == null ? null : new TestFile(super.getParentFile());
    }

    @Override
    public String toString() {
        return getPath();
    }

    public TestFile writelns(Iterable<String> lines) {
        Formatter formatter = new Formatter();
        for (String line : lines) {
            formatter.format("%s%n", line);
        }
        return write(formatter);
    }

    /**
     * Replaces the given text in the file with new value, asserting that the change was actually applied (ie the text was present).
     */
    public void replace(String oldText, String newText) {
        String original = getText();
        String newContent = original.replace(oldText, newText);
        if (original.equals(newContent)) {
            throw new AssertionError("File " + this + " does not contain the expected text.");
        }
        setText(newContent);
    }

    /**
     * Inserts the given text at the start of the file
     */
    public void prepend(String text) {
        String original = getText();
        setText(text + original);
    }

    /**
     * Inserts the given text on a new line before the given text
     */
    public void insertBefore(String text, String newText) {
        String original = getText();
        int pos = original.indexOf(text);
        if (pos < 0) {
            throw new AssertionError("File " + this + " does not contain the expected text.");
        }
        StringBuilder newContent = new StringBuilder(original);
        newContent.insert(pos, '\n');
        newContent.insert(pos, newText);
        setText(newContent.toString());
    }

    public TestFile assertExists() {
        assertTrue(String.format("%s does not exist", this), exists());
        return this;
    }

    public TestFile assertIsFile() {
        assertTrue(String.format("%s is not a file", this), isFile());
        return this;
    }

    public TestFile assertIsDir() {
        return assertIsDir("");
    }

    public TestFile assertIsDir(String hint) {
        assertTrue(String.format("%s is not a directory. %s", this, hint), isDirectory());
        return this;
    }

    public TestFile assertDoesNotExist() {
        if (exists()) {
            Set<String> descendants = new TreeSet<>();
            if (isFile()) {
                throw new AssertionError(String.format("%s should not exist", this));
            } else {
                visit(descendants, "", this, false);
                throw new AssertionError(String.format("%s should not exist:\n%s", this, String.join("\n", descendants)));
            }
        }
        return this;
    }

    public TestFile assertContents(Matcher<String> matcher) {
        assertThat(getText(), matcher);
        return this;
    }

    public TestFile assertIsCopyOf(TestFile other) {
        assertIsFile();
        other.assertIsFile();
        assertEquals(String.format("%s is not the same length as %s", this, other), other.length(), this.length());
        assertEquals(String.format("%s does not have the same content as %s", this, other), getMd5Hash(), other.getMd5Hash());
        return this;
    }

    public TestFile assertIsDifferentFrom(TestFile other) {
        assertIsFile();
        other.assertIsFile();
        assertHasChangedSince(other.snapshot());
        return this;
    }

    public String getMd5Hash() {
        return md5(this).toString();
    }

    public static HashCode md5(File file) {
        try {
            return Hashing.hashFile(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public TestFile createLink(String target) {
        return createLink(new File(target));
    }

    public TestFile createLink(File target) {
        if (Files.isSymbolicLink(this.toPath())) {
            try {
                Files.delete(toPath());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        try {
            getParentFile().mkdirs();
            Files.createSymbolicLink(this.toPath(), target.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        clearCanonCaches();
        return this;
    }

    public TestFile createNamedPipe() {
        try {
            Process mkfifo = new ProcessBuilder("mkfifo", getAbsolutePath())
                .redirectErrorStream(true)
                .start();
            assert mkfifo.waitFor() == 0; // assert the exit value signals success
            return this;
        } catch (IOException | InterruptedException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void clearCanonCaches() {
        try {
            File.createTempFile("doesnt", "matter").delete();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String readLink() {
        assertExists();
        return new TestFileHelper(this).readLink();
    }

    public String getPermissions() {
        assertExists();
        return new TestFileHelper(this).getPermissions();
    }

    public TestFile setPermissions(String permissions) {
        assertExists();
        new TestFileHelper(this).setPermissions(permissions);
        return this;
    }

    public TestFile setMode(int mode) {
        assertExists();
        new TestFileHelper(this).setMode(mode);
        return this;
    }

    public int getMode() {
        assertExists();
        return new TestFileHelper(this).getMode();
    }

    /**
     * Asserts that this is a directory and contains exactly the given set of descendant files and child directories. Ignores directories that are not empty.
     */
    public TestFile assertHasDescendants(String... descendants) {
        return assertHasDescendants(Arrays.asList(descendants));
    }

    /**
     * Asserts that this is a directory and contains exactly the given set of descendant files and child directories. Ignores directories that are not empty.
     */
    public TestFile assertHasDescendants(Iterable<String> descendants) {
        return assertHasDescendants(descendants, false);
    }

    public TestFile assertHasDescendants(Iterable<String> descendants, boolean ignoreDirs) {
        Set<String> actual = new TreeSet<>();
        assertIsDir();
        visit(actual, "", this, ignoreDirs);
        Set<String> expected = new TreeSet<>(Lists.newArrayList(descendants));

        Set<String> extras = new TreeSet<>(actual);
        extras.removeAll(expected);
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);

        assertEquals(String.format("For dir: %s\n extra files: %s, missing files: %s, expected: %s", this, extras, missing, expected), expected, actual);

        return this;
    }

    /**
     * Asserts that this is a directory and contains the given set of descendant files and child directories (and possibly other files). Ignores directories that are not empty.
     */
    public TestFile assertContainsDescendants(Iterable<String> descendants) {
        assertIsDir();
        Set<String> actual = new TreeSet<>();
        visit(actual, "", this, false);

        Set<String> expected = new TreeSet<>(Lists.newArrayList(descendants));

        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);

        assertTrue(String.format("For dir: %s\n missing files: %s, expected: %s, actual: %s", this, missing, expected, actual), missing.isEmpty());

        return this;
    }

    /**
     * Asserts that this is a directory and contains the given set of descendant files (and possibly other files). Ignores directories that are not empty.
     */
    public TestFile assertContainsDescendants(String... descendants) {
        return assertContainsDescendants(Arrays.asList(descendants));
    }

    public TestFile assertIsEmptyDir() {
        assertHasDescendants();
        return this;
    }

    public Set<String> allDescendants() {
        Set<String> names = new TreeSet<>();
        if (isDirectory()) {
            visit(names, "", this, false);
        }
        return names;
    }

    private void visit(Set<String> names, String prefix, File file, boolean ignoreDirs) {
        assert file.isDirectory();
        for (File child : file.listFiles()) {
            if (child.isFile() || !ignoreDirs && child.isDirectory() && child.list().length == 0) {
                names.add(prefix + child.getName());
            } else if (child.isDirectory()) {
                visit(names, prefix + child.getName() + "/", child, ignoreDirs);
            }
        }
    }

    public boolean isSelfOrDescendent(File file) {
        if (file.getAbsolutePath().equals(getAbsolutePath())) {
            return true;
        }
        return isDescendent(file);
    }

    public boolean isDescendent(File file) {
        return file.getAbsolutePath().startsWith(getAbsolutePath() + File.separatorChar);
    }

    public TestFile createDir() {
        if (mkdirs()) {
            return this;
        }
        if (isDirectory()) {
            return this;
        }
        throw new AssertionError("Problems creating dir: " + this
            + ". Diagnostics: exists=" + this.exists() + ", isFile=" + this.isFile() + ", isDirectory=" + this.isDirectory());
    }

    public TestFile createDir(Object path) {
        return new TestFile(this, path).createDir();
    }

    public TestFile deleteDir() {
        new TestFileHelper(this).delete(useNativeTools);
        return this;
    }

    /**
     * Attempts to delete this directory, ignoring failures to do so.
     *
     * @return this
     */
    public TestFile maybeDeleteDir() {
        try {
            deleteDir();
        } catch (RuntimeException e) {
            // Ignore
        }
        return this;
    }

    /**
     * Recursively delete this directory, reporting all failed paths.
     */
    public TestFile forceDeleteDir() throws IOException {
        if (isDirectory()) {
            if (FileUtils.isSymlink(this)) {
                if (!delete()) {
                    throw new IOException("Unable to delete symlink: " + getCanonicalPath());
                }
            } else {
                List<String> errorPaths = new ArrayList<>();
                Files.walkFileTree(toPath(), new SimpleFileVisitor<Path>() {

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (!file.toFile().delete()) {
                            errorPaths.add(file.toFile().getCanonicalPath());
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        if (!dir.toFile().delete()) {
                            errorPaths.add(dir.toFile().getCanonicalPath());
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
                if (!errorPaths.isEmpty()) {
                    StringBuilder builder = new StringBuilder()
                        .append("Unable to recursively delete directory ")
                        .append(getCanonicalPath())
                        .append(", failed paths:\n");
                    for (String errorPath : errorPaths) {
                        builder.append("\t- ").append(errorPath).append("\n");
                    }
                    throw new IOException(builder.toString());
                }
            }
        } else if (exists()) {
            if (!delete()) {
                throw new IOException("Unable to delete file: " + getCanonicalPath());
            }
        }
        return this;
    }

    public TestFile createFile() {
        new TestFile(getParentFile()).createDir();
        try {
            assertTrue(isFile() || createNewFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public TestFile makeUnreadable() {
        boolean success = setReadable(false, false);
        assert success;
        assert !Files.isReadable(toPath());
        return this;
    }

    public TestFile makeReadable() {
        boolean success = setReadable(true, false);
        assert success;
        assert Files.isReadable(toPath());
        return this;
    }

    public TestFile createFile(Object path) {
        return file(path).createFile();
    }

    public TestFile createZip(Object path) {
        Zip zip = new Zip();
        zip.setWhenempty((Zip.WhenEmpty) Zip.WhenEmpty.getInstance(Zip.WhenEmpty.class, "create"));
        TestFile zipFile = file(path);
        zip.setDestFile(zipFile);
        zip.setBasedir(this);
        zip.setExcludes("**");
        zip.setProject(new Project());
        zip.execute();
        return zipFile;
    }

    public TestFile zipTo(TestFile zipFile) {
        return zipTo(zipFile, false);
    }

    public TestFile zipTo(TestFile zipFile, boolean readOnly) {
        new TestFileHelper(this).zipTo(zipFile, useNativeTools, readOnly);
        return this;
    }

    public TestFile tarTo(TestFile tarFile) {
        return tarTo(tarFile, false);
    }

    public TestFile tarTo(TestFile tarFile, boolean readOnly) {
        new TestFileHelper(this).tarTo(tarFile, useNativeTools, readOnly);
        return this;
    }

    public TestFile tgzTo(TestFile tarFile) {
        return tgzTo(tarFile, false);
    }

    public TestFile tgzTo(TestFile tarFile, boolean readOnly) {
        new TestFileHelper(this).tgzTo(tarFile, readOnly);
        return this;
    }

    public TestFile tbzTo(TestFile tarFile) {
        return tbzTo(tarFile, false);
    }

    public TestFile tbzTo(TestFile tarFile, boolean readOnly) {
        new TestFileHelper(this).tbzTo(tarFile, readOnly);
        return this;
    }

    public TestFile bzip2To(TestFile compressedFile) {
        new TestFileHelper(this).bzip2To(compressedFile);
        return this;
    }

    public TestFile gzipTo(TestFile compressedFile) {
        new TestFileHelper(this).gzipTo(compressedFile);
        return this;
    }

    public Snapshot snapshot() {
        assertIsFile();
        return new Snapshot(lastModified(), md5(this));
    }

    public void assertHasChangedSince(Snapshot snapshot) {
        Snapshot now = snapshot();
        assertTrue(String.format("contents or modification time of %s have not changed", this), now.modTime != snapshot.modTime || !now.hash.equals(snapshot.hash));
    }

    public void assertContentsHaveChangedSince(Snapshot snapshot) {
        Snapshot now = snapshot();
        assertNotEquals(String.format("contents of %s have not changed", this), snapshot.hash, now.hash);
    }

    public void assertContentsHaveNotChangedSince(Snapshot snapshot) {
        Snapshot now = snapshot();
        assertEquals(String.format("contents of %s has changed", this), snapshot.hash, now.hash);
    }

    public void assertHasNotChangedSince(Snapshot snapshot) {
        Snapshot now = snapshot();
        assertEquals(String.format("last modified time of %s has changed", this), snapshot.modTime, now.modTime);
        assertEquals(String.format("contents of %s has changed", this), snapshot.hash, now.hash);
    }

    public void writeProperties(Map<?, ?> properties) {
        Properties props = new Properties();
        props.putAll(properties);
        try (FileOutputStream stream = new FileOutputStream(this)) {
            props.store(stream, "comment");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void assumeExists() {
        assumeTrue(this.exists());
    }

    public ExecOutput exec(Object... args) {
        return new TestFileHelper(this).executeSuccess(Arrays.asList(args), null);
    }

    public ExecOutput execWithFailure(List<?> args, List<?> env) {
        return new TestFileHelper(this).executeFailure(args, env);
    }

    public ExecOutput execute(List<?> args, List<?> env) {
        return new TestFileHelper(this).executeSuccess(args, env);
    }

    /**
     * Relativizes the URI of this file according to the base directory.
     */
    public URI relativizeFrom(TestFile baseDir) {
        return baseDir.toURI().relativize(toURI());
    }

    /**
     * Returns a human-readable relative path to this file from the base directory passed to create this TestFile.
     *
     * Fails if this TestFile was created in a way that did not provide a relative base.
     *
     * @see #relativizeFrom(TestFile)
     * @see java.nio.file.Path#relativize(Path)
     */
    public String getRelativePathFromBase() {
        assertNotEquals("relativeBase must have been set during construction", relativeBase.toPath(), this.toPath());
        return relativeBase.toPath().relativize(this.toPath()).toString();
    }

    public static class Snapshot {
        private final long modTime;
        private final HashCode hash;

        public Snapshot(long modTime, HashCode hash) {
            this.modTime = modTime;
            this.hash = hash;
        }

        public long getModTime() {
            return modTime;
        }

        public HashCode getHash() {
            return hash;
        }
    }
}
