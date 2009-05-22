package org.gradle.api.changedetection.state;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.CompareToBuilder;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 * @author Tom Eyckmans
 */
class DirectoryState implements Comparable {
    private final File directory;
    private final String relativePath;
    private final String relativePathDigest;
    private final int level;
    private final Map<String, String> fileDigests;
    private String digest;
    private long size;
    private Throwable failureCause;

    DirectoryState(File directory, String relativePath, String relativePathDigest, int level) {
        if ( directory == null ) throw new IllegalArgumentException("directory is null!");
        if ( StringUtils.isEmpty(relativePath) ) throw new IllegalArgumentException("relativePath is empty!");
        if ( StringUtils.isEmpty(relativePathDigest) ) throw new IllegalArgumentException("relativePathDigest is empty!");
        if ( level < 0 ) throw new IllegalArgumentException("level is smaller than zero!");
        
        this.directory = directory;
        this.relativePath = relativePath;
        this.relativePathDigest = relativePathDigest;
        this.level = level;
        this.fileDigests = new HashMap<String, String>();
    }

    public File getDirectory() {
        return directory;
    }

    public int getLevel() {
        return level;
    }

    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();

        builder.append(level);
        builder.append(relativePath);

        return builder.toHashCode();
    }

    public boolean equals(Object o) {
        final DirectoryState other = (DirectoryState)o;
        final EqualsBuilder builder = new EqualsBuilder();

        builder.append(other.level, level);
        builder.append(relativePath, other.relativePath);

        return builder.isEquals();
    }

    public int compareTo(Object o) {
        final DirectoryState other = (DirectoryState)o;
        final CompareToBuilder builder = new CompareToBuilder();

        builder.append(other.level, level);
        builder.append(relativePath, other.relativePath);

        return builder.toComparison();
    }

    public String toString() {
        final StringBuilder strBuilder = new StringBuilder();

        strBuilder.append("[");
        strBuilder.append(level);
        strBuilder.append("] ");
        strBuilder.append(relativePath);
        strBuilder.append(" : ");
        strBuilder.append(digest);
        strBuilder.append(" : ");
        strBuilder.append(FileUtils.byteCountToDisplaySize(size));

        return strBuilder.toString();
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getRelativePathDigest() {
        return relativePathDigest;
    }

    public Map<String, String> getFileDigests() {
        return Collections.unmodifiableMap(fileDigests);
    }

    public void addFileDigest(String filename, String digest) {
        fileDigests.put(filename, digest);
    }

    public String getDigest() {
        return digest;
    }

    public void setDigest(String digest) {
        this.digest = digest;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setFailureCause(Throwable failureCause) {
        this.failureCause = failureCause;
    }

    public Throwable getFailureCause() {
        return failureCause;
    }
}
