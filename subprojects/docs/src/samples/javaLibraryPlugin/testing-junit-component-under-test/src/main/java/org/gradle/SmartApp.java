package org.gradle;

import java.util.Properties;

public class SmartApp {
    private static final double FACTOR;

    static {
        double factor = 0;
        try {
            Properties props = new Properties();
            props.load(SmartApp.class.getResourceAsStream("resource.properties"));
            factor = Double.valueOf(props.getProperty("factor", "0"));
        } catch (Exception ex) {
        } finally {
            FACTOR = factor;
        }
    }

    public double smartComputation(double x) {
        return FACTOR*x;
    }
}
