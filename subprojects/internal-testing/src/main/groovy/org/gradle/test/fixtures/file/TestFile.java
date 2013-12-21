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

import groovy.lang.Closure;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Tar;
import org.apache.tools.ant.taskdefs.Zip;
import org.apache.tools.ant.types.EnumeratedAttribute;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.internal.nativeplatform.filesystem.*;
import org.gradle.internal.nativeplatform.services.NativeServices;
import org.hamcrest.Matcher;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static org.junit.Assert.*;

public class TestFile extends File {
    private boolean useNativeTools;

    public TestFile(File file, Object... path) {
        super(join(file, path).getAbsolutePath());
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
        List<TestFile> files = new ArrayList<TestFile>();
        for (Object path : paths) {
            files.add(file(path));
        }
        return files;
    }

    public TestFile withExtension(String extension) {
        return getParentFile().file(getName().replaceAll("\\..*$", "." + extension));
    }

    public TestFile writelns(String... lines) {
        return writelns(Arrays.asList(lines));
    }

    public TestFile write(Object content) {
        try {
            FileUtils.writeStringToFile(this, content.toString());
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not write to test file '%s'", this), e);
        }
        return this;
    }

    public TestFile leftShift(Object content) {
        getParentFile().mkdirs();
        try {
            DefaultGroovyMethods.leftShift(this, content);
            return this;
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not append to test file '%s'", this), e);
        }
    }

    public TestFile[] listFiles() {
        File[] children = super.listFiles();
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
            return FileUtils.readFileToString(this);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not read from test file '%s'", this), e);
        }
    }

    public Map<String, String> getProperties() {
        assertIsFile();
        Properties properties = new Properties();
        try {
            FileInputStream inStream = new FileInputStream(this);
            try {
                properties.load(inStream);
            } finally {
                inStream.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Map<String, String> map = new HashMap<String, String>();
        for (Object key : properties.keySet()) {
            map.put(key.toString(), properties.getProperty(key.toString()));
        }
        return map;
    }

    public Manifest getManifest() {
        assertIsFile();
        try {
            JarFile jarFile = new JarFile(this);
            try {
                return jarFile.getManifest();
            } finally {
                jarFile.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> linesThat(Matcher<? super String> matcher) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(this));
            try {
                List<String> lines = new ArrayList<String>();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (matcher.matches(line)) {
                        lines.add(line);
                    }
                }
                return lines;
            } finally {
                reader.close();
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
                FileUtils.copyDirectory(this, target);
            } catch (IOException e) {
                throw new RuntimeException(String.format("Could not copy test directory '%s' to '%s'", this,
                        target), e);
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

    public void copyFrom(URL resource) {
        try {
            FileUtils.copyURLToFile(resource, this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
    public TestFile create(Closure structure) {
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

    public TestFile assertExists() {
        assertTrue(String.format("%s does not exist", this), exists());
        return this;
    }

    public TestFile assertIsFile() {
        assertTrue(String.format("%s is not a file", this), isFile());
        return this;
    }

    public TestFile assertIsDir() {
        assertTrue(String.format("%s is not a directory", this), isDirectory());
        return this;
    }

    public TestFile assertDoesNotExist() {
        assertFalse(String.format("%s should not exist", this), exists());
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
        assertTrue(String.format("%s does not have the same content as %s", this, other), Arrays.equals(getHash("MD5"), other.getHash("MD5")));
        return this;
    }

    private byte[] getHash(String algorithm) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(algorithm);
            messageDigest.update(FileUtils.readFileToByteArray(this));
            return messageDigest.digest();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void createLink(File target) {
        createLink(target.getAbsolutePath());
    }

    public void createLink(String target) {
        try {
            NativeServices.getInstance().get(FileSystem.class).createSymbolicLink(this, new File(target));
        } catch (IOException e) {
            throw new RuntimeException(e);
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
     * Asserts that this file contains exactly the given set of descendants.
     */
    public TestFile assertHasDescendants(String... descendants) {
        Set<String> actual = new TreeSet<String>();
        assertIsDir();
        visit(actual, "", this);
        Set<String> expected = new TreeSet<String>(Arrays.asList(descendants));

        Set<String> extras = new TreeSet<String>(actual);
        extras.removeAll(expected);
        Set<String> missing = new TreeSet<String>(expected);
        missing.removeAll(actual);

        assertEquals(String.format("For dir: %s, extra files: %s, missing files: %s, expected: %s", this, extras, missing, expected), expected, actual);

        return this;
    }

    public TestFile assertIsEmptyDir() {
        if (exists()) {
            assertIsDir();
            assertHasDescendants();
        }
        return this;
    }

    private void visit(Set<String> names, String prefix, File file) {
        for (File child : file.listFiles()) {
            if (child.isFile()) {
                names.add(prefix + child.getName());
            } else if (child.isDirectory()) {
                visit(names, prefix + child.getName() + "/", child);
            }
        }
    }

    public boolean isSelfOrDescendent(File file) {
        if (file.getAbsolutePath().equals(getAbsolutePath())) {
            return true;
        }
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

    public TestFile createFile() {
        new TestFile(getParentFile()).createDir();
        try {
            assertTrue(isFile() || createNewFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        execute(zip);
        return zipFile;
    }

    public TestFile zipTo(TestFile zipFile){
        new TestFileHelper(this).zipTo(zipFile, useNativeTools);
        return this;
    }

    public TestFile tarTo(TestFile tarFile) {
        new TestFileHelper(this).tarTo(tarFile, useNativeTools);
        return this;
    }

    public TestFile tgzTo(TestFile tarFile) {
        Tar tar = new Tar();
        tar.setBasedir(this);
        tar.setDestFile(tarFile);
        tar.setCompression((Tar.TarCompressionMethod) EnumeratedAttribute.getInstance(Tar.TarCompressionMethod.class, "gzip"));
        execute(tar);
        return this;
    }

    public TestFile tbzTo(TestFile tarFile) {
        Tar tar = new Tar();
        tar.setBasedir(this);
        tar.setDestFile(tarFile);
        tar.setCompression((Tar.TarCompressionMethod) EnumeratedAttribute.getInstance(Tar.TarCompressionMethod.class, "bzip2"));
        execute(tar);
        return this;
    }

    private void execute(Task task) {
        task.setProject(new Project());
        task.execute();
    }

    public Snapshot snapshot() {
        assertIsFile();
        return new Snapshot(lastModified(), getHash("MD5"));
    }

    public void assertHasChangedSince(Snapshot snapshot) {
        Snapshot now = snapshot();
        assertTrue(now.modTime != snapshot.modTime || !Arrays.equals(now.hash, snapshot.hash));
    }

    public void assertContentsHaveChangedSince(Snapshot snapshot) {
        Snapshot now = snapshot();
        assertTrue(String.format("contents of %s have not changed", this), !Arrays.equals(now.hash, snapshot.hash));
    }

    public void assertContentsHaveNotChangedSince(Snapshot snapshot) {
        Snapshot now = snapshot();
        assertArrayEquals(String.format("contents of %s has changed", this), snapshot.hash, now.hash);
    }

    public void assertHasNotChangedSince(Snapshot snapshot) {
        Snapshot now = snapshot();
        assertEquals(String.format("last modified time of %s has changed", this), snapshot.modTime, now.modTime);
        assertArrayEquals(String.format("contents of %s has changed", this), snapshot.hash, now.hash);
    }

    public void writeProperties(Map<?, ?> properties) {
        Properties props = new Properties();
        props.putAll(properties);
        try {
            FileOutputStream stream = new FileOutputStream(this);
            try {
                props.store(stream, "comment");
            } finally {
                stream.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public ExecOutput exec(Object... args) {
        return new TestFileHelper(this).execute(Arrays.asList(args), null);
    }

    public ExecOutput execute(List args, List env) {
        return new TestFileHelper(this).execute(args, env);
    }

    public class Snapshot {
        private final long modTime;
        private final byte[] hash;

        public Snapshot(long modTime, byte[] hash) {
            this.modTime = modTime;
            this.hash = hash;
        }
    }
}
