package org.gradle.api.changedetection.state;

import org.apache.commons.lang.StringUtils;

/**
 * @author Tom Eyckmans
 */
class StateFileItem {
    private final String key;
    private final String digest;

    StateFileItem(String key, String digest) {
        if ( key == null ) throw new IllegalArgumentException("key is null!");
        if ( StringUtils.isEmpty(digest) ) throw new IllegalArgumentException("digest is empty!");
        this.key = key;
        this.digest = digest;
    }

    public String getKey() {
        return key;
    }

    public String getDigest() {
        return digest;
    }
}
