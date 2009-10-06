package org.gradle.api.testing.fabric;

import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * @author Tom Eyckmans
 */
public class DefaultTestClassRunInfo implements TestClassRunInfo {
    private String testClassName;

    public DefaultTestClassRunInfo(String testClassName) {
        if (StringUtils.isEmpty(testClassName)) throw new IllegalArgumentException("testClassName is empty!");

        this.testClassName = testClassName;
    }

    public String getTestClassName() {
        return testClassName;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeUTF(testClassName);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        testClassName = in.readUTF();
    }
}
