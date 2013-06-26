package org.gradle.util;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to gracefully handle multiple I/O resources
 *
 * <p>This class is inspired by Guava's <a
 * href="http://docs.guava-libraries.googlecode.com/git/javadoc/com/google/common/io/Closer.html">
 * {@code Closer}</a>; it adds additional supports for {@link Flushable} as
 * well.
 * </p>
 *
 * <p>The principle is to handle multiple I/O resources gracefully, from their
 * opening to their closing. While the traditional idiom (open before {@code
 * try}, close in {@code finally}) works well for one I/O resource, it is not
 * sustainable for more than one resource. This class fixes that.</p>
 *
 * <p>Sample usage:</p>
 *
 * <pre>
 *     InputStream in1, in2;
 *     OutputStream out;
 *     final CloserFlusher cf = new CloserFlusher();
 *     try {
 *         in1 = cf.add(whatever());
 *         in2 = cf.add(whatever());
 *         out = cf.add(whatever());
 *         doStuffWith(in1, in2, out);
 *         //optionally:
 *         cf.flush();
 *     } finally {
 *         cf.close();
 *     }
 * </pre>
 *
 * <p>Added {@code Closeable}s are closed in <i>reverse</i> order; even if one
 * should fail to close with an {@link IOException}, other resources are still
 * tried.</p>
 *
 * <p>{@code Flushable}s, when {@link #flush()} is closed, are flushed in the
 * order in which the resources were registered using {@link #add(Closeable)}.
 * </p>
 */
public final class CloserFlusher
    implements Closeable, Flushable
{
    /*
     * Implicit public constructor: not declared.
     */

    /*
     * Our lists of closeables and flushables
     */
    private final List<Closeable> closeables = new ArrayList<Closeable>();
    private final List<Flushable> flushables = new ArrayList<Flushable>();

    /**
     * Add a resource to this closer/flusher
     *
     * <p>As a resource <i>can</i> fail to open due to an I/O exception, this
     * method throws it back in this case.</p>
     *
     * <p>Example:</p>
     *
     * <pre>
     *     in = cf.add(new FileInputStream("whatever"));
     * </pre>
     *
     * @param closeable the resource
     * @param <C> type of the closeable
     * @return the created closeable
     * @throws IOException error initializing the resource
     */
    public <C extends Closeable> C add(final C closeable)
        throws IOException
    {
        closeables.add(closeable);
        if (closeable instanceof Flushable)
            flushables.add((Flushable) closeable);
        return closeable;
    }


    /**
     * Close all registered resources
     *
     * <p>Resources are closed in the reverse order in which they were added.
     * </p>
     *
     * <p>The exception thrown is the one of the first resource which failed to
     * close correctly.</p>
     *
     * @throws IOException see description
     */
    @Override
    public void close()
        throws IOException
    {
        final int csize = closeables.size();
        IOException thrown = null;

        for (int i = csize - 1; i >= 0; i--)
            try {
                closeables.get(i).close();
            } catch (IOException e) {
                if (thrown == null)
                    thrown = e;
            }

        if (thrown != null)
            throw thrown;
    }

    /**
     * Flush this closer/flusher
     *
     * <p>All resources see a flush attempt. The exception thrown is the one of
     * the first resource to fail to flush correctly.</p>
     *
     * @throws IOException see description
     */
    @Override
    public void flush()
        throws IOException
    {
        final int fsize = flushables.size();
        IOException thrown = null;

        for (final Flushable flushable : flushables) {
            try {
                flushable.flush();
            } catch (IOException e) {
                if (thrown == null) {
                    thrown = e;
                }
            }
        }

        if (thrown != null)
            throw thrown;
    }

    /**
     * Close this closer/flusher quietly
     *
     * <p>Internally, it just calls {@link #close()} but ignores the thrown
     * exception. Which is not recommended!</p>
     */
    public void closeQuietly()
    {
        try {
            close();
        } catch (IOException ignored) {
        }
    }

    /**
     * Flush this closer/flusher quietly
     *
     * <p>Internally, it just calls {@link #flush()} but ignores the thrown
     * exception. Which is not recommended!</p>
     */
    public void flushQuietly()
    {
        try {
            flush();
        } catch (IOException ignored) {
        }
    }
}
