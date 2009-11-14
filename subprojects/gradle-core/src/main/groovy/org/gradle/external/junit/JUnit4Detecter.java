package org.gradle.external.junit;

/**
 * @author Tom Eyckmans
 */
public class JUnit4Detecter {
    private static final JUnit4Detecter detecter = new JUnit4Detecter();

    private final boolean junit4;
    private JUnit4Detecter() {
        Class junit4TestAdapterClass = null;

        try {
            junit4TestAdapterClass = Class.forName("junit.framework.JUnit4TestAdapter");
        }
        catch (ClassNotFoundException noJunit4Exception) {
            // else JUnit 3
        }

        junit4 = junit4TestAdapterClass != null;
    }

    public static boolean isJUnit4Available()
    {
        return detecter.junit4;
    }
}
