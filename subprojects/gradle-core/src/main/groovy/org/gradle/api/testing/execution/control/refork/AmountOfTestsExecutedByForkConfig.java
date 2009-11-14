package org.gradle.api.testing.execution.control.refork;

/**
 * @author Tom Eyckmans
 */
public class AmountOfTestsExecutedByForkConfig implements ReforkReasonConfig {

    private long reforkEvery;

    public AmountOfTestsExecutedByForkConfig() {
    }

    public AmountOfTestsExecutedByForkConfig(long reforkEvery) {
        setReforkEvery(reforkEvery);
    }

    public long getReforkEvery() {
        return reforkEvery;
    }

    public void setReforkEvery(long reforkEvery) {
        if ( reforkEvery <= 0 ) throw new IllegalArgumentException("reforkEvery needs to be larger than zero!");

        this.reforkEvery = reforkEvery;
    }
}
