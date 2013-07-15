package org.gradle.api.internal.file.copy;

import org.gradle.api.Action;

public class CopySpecWalker {

    public void visit(CopySpecInternal copySpec, Action<? super CopySpecInternal> action) {
        action.execute(copySpec);
        for (CopySpecInternal child : copySpec.getChildren()) {
            visit(child, action);
        }
    }

}
