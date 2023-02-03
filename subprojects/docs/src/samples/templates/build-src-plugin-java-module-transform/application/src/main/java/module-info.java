module org.gradle.sample.app {
    exports org.gradle.sample.app;
    opens org.gradle.sample.app.data; // allow Gson to access via reflection

    requires com.google.gson;
    requires org.apache.commons.lang3;
    requires org.apache.commons.cli;
    requires org.apache.commons.beanutils;
}
