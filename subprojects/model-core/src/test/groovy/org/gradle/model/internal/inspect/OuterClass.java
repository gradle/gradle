package org.gradle.model.internal.inspect;

public class OuterClass {
    public static abstract class AbstractClass {
    }

    public static interface AnInterface {
    }

    public class InnerInstanceClass {
    }

    private class PrivateInnerStaticClass {
    }

    public static class HasSuperclass extends InnerPublicStaticClass {
    }

    public static class HasTwoConstructors {
        public HasTwoConstructors() {
        }

        public HasTwoConstructors(String arg) {
        }
    }

    public static class HasInstanceVar {
        public String getFoo() {
            return foo;
        }

        public void setFoo(String foo) {
            this.foo = foo;
        }

        private String foo;
    }

    public static class HasFinalInstanceVar {
        public final String getFoo() {
            return foo;
        }

        private final String foo = null;
    }

    public static class HasNonFinalStaticVar {
        public static String getFoo() {
            return foo;
        }

        public static void setFoo(String foo) {
            HasNonFinalStaticVar.foo = foo;
        }

        private static String foo = null;
    }

    public static class InnerPublicStaticClass {
    }

    public static class HasExplicitDefaultConstructor {
        public HasExplicitDefaultConstructor() {
        }
    }

    public static class HasStaticFinalField {
        public static Object getVALUE() {
            return VALUE;
        }

        private static final Object VALUE = null;
    }
}
