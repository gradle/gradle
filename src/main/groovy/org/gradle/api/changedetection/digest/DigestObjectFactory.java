package org.gradle.api.changedetection.digest;

/**
 * @author Tom Eyckmans
 */
public class DigestObjectFactory {
    public static DigesterCache createDigesterCache(final DigesterFactory digesterFactory) {
        return new DefaultDigesterCache(digesterFactory);
    }

    public static DigesterCache createShaDigesterCache() {
        return createDigesterCache(createShaDigesterFactory());
    }

    public static DigesterUtil createDigesterUtil(final DigesterUtilStrategy digesterUtilStrategy) {
        return new DefaultDigesterUtil(digesterUtilStrategy);
    }

    public static DigesterUtil createMetaDigesterUtil() {
        return createDigesterUtil(new MetaDigesterUtilStrategy());
    }

    public static DigesterUtil createMetaContentDigesterUtil() {
        return createDigesterUtil(new MetaContentDigesterUtilStrategy());
    }

    public static DigesterFactory createShaDigesterFactory() {
        return new ShaDigesterFactory();
    }
}
