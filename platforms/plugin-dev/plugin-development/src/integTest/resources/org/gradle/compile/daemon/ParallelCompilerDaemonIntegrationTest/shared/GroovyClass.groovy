package org.gradle.compile.daemon.ParallelCompilerDaemonIntegrationTest.shared
/**
 * An immutable classpath.
 */
public class GroovyClass implements Serializable {
    private final List<File> files;

    public GroovyClass(Iterable<File> files) {
        this.files = new ArrayList<File>();
        for (File file : files) {
            this.files.add(file);
        }
    }

    public GroovyClass(File... files) {
        this(Arrays.asList(files));
    }

    @Override
    public String toString() {
        return files.toString();
    }

    public boolean isEmpty() {
        return files.isEmpty();
    }

    public Collection<URI> getAsURIs() {
        List<URI> urls = new ArrayList<URI>();
        for (File file : files) {
            urls.add(file.toURI());
        }
        return urls;
    }

    public Collection<File> getAsFiles() {
        return files;
    }

    public URL[] getAsURLArray() {
        Collection<URL> result = getAsURLs();
        return result.toArray(new URL[result.size()]);
    }

    public Collection<URL> getAsURLs() {
        List<URL> urls = new ArrayList<URL>();
        for (File file : files) {
            try {
                urls.add(file.toURI().toURL());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
        return urls;
    }

    public GroovyClass plus(GroovyClass other) {
        if (files.isEmpty()) {
            return other;
        }
        if (other.isEmpty()) {
            return this;
        }
        return new GroovyClass(concat(files, other.getAsFiles()));
    }

    public GroovyClass plus(Collection<File> other) {
        if (other.isEmpty()) {
            return this;
        }
        return new GroovyClass(concat(files, other));
    }

    private Iterable<File> concat(List<File> files1, Collection<File> files2) {
        List<File> result = new ArrayList<File>();
        result.addAll(files1);
        result.addAll(files2);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        GroovyClass other = (GroovyClass) obj;
        return files.equals(other.files);
    }

    @Override
    public int hashCode() {
        return files.hashCode();
    }
}
