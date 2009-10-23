package org.gradle.api.testing.fabric;

import org.apache.commons.lang.StringUtils;

/**
 * @author Tom Eyckmans
 */
public class DefaultTestClassRunInfoFactory implements TestClassRunInfoFactory {
    public TestClassRunInfo createTestClassRunInfo(String testClassName) {
        if (StringUtils.isNotEmpty(testClassName)) {
            // normalize the testClassName
            final String javaTestClassName = testClassName
                .replaceAll("/", ".")
                .replaceAll("\\.class", "");

            return new DefaultTestClassRunInfo(javaTestClassName);
        }
        else
            return null;
    }
}
