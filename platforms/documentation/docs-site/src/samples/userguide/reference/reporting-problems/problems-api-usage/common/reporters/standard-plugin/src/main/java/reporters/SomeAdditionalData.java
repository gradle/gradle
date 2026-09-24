package reporters;

import org.gradle.api.problems.AdditionalData;
import org.gradle.api.provider.Property;

import java.util.List;

public interface SomeAdditionalData extends AdditionalData {
    Property<String> getSome();

    String getName();

    void setName(String name);

    List<String> getNames();

    void setNames(List<String> names);
}
