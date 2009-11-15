package org.gradle.external.junit;

import junit.framework.Test;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractJUnitTestClassHandler implements JUnitTestClassHandler {
    protected junit.framework.Test suite;

    protected AbstractJUnitTestClassHandler() {
    }

    protected AbstractJUnitTestClassHandler(Test suite) {
        this.suite = suite;
    }

    public final Test getSuite() {
        return suite;
    }
}
