package org.gradle.api.changedetection.digest;

import org.apache.commons.lang.StringUtils;

import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Tom Eyckmans
 */
class DefaultDigesterCache implements DigesterCache{

    private final Map<String, MessageDigest> cache;
    private final DigesterFactory digesterFactory;

    DefaultDigesterCache(DigesterFactory digesterFactory) {
        if ( digesterFactory == null ) throw new IllegalArgumentException("digesterFactory is null!");

        this.cache = new ConcurrentHashMap<String, MessageDigest>();
        this.digesterFactory = digesterFactory;
    }

    public MessageDigest getDigester(String cacheId) {
        if ( StringUtils.isEmpty(cacheId) ) throw new IllegalArgumentException("cacheId is empty!");

        MessageDigest digester = cache.get(cacheId);
        if ( digester == null ) {
            digester = digesterFactory.createDigester();
            cache.put(cacheId, digester);
        }
        else {
            digester.reset();
        }
        
        return digester;
    }

    public DigesterFactory getDigesterFactory() {
        return digesterFactory;
    }

}
