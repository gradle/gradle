package org.gradle.api.testing.reporting;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.testing.reporting.policies.ReportPolicyConfig;

/**
 * @author Tom Eyckmans
 */
public class ReportConfig {
    private String name;
    private ReportPolicyConfig policyConfig;

    public ReportConfig(String name) {
        setName(name);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if ( StringUtils.isEmpty(name) ) throw new IllegalArgumentException(("name is empty!"));

        this.name = name;
    }

    public ReportPolicyConfig getPolicyConfig() {
        return policyConfig;
    }

    public void setPolicyConfig(ReportPolicyConfig policyConfig) {
        if ( policyConfig == null ) throw new IllegalArgumentException("policyConfig == null!");
        
        this.policyConfig = policyConfig;
    }
}
