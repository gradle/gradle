package org.gradle.webapp1;

/**
 * Created by IntelliJ IDEA.
 * User: hans
 * Date: Oct 23, 2007
 * Time: 5:40:28 PM
 * To change this template use File | Settings | File Templates.
 */
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.collections.list.GrowthList;
import org.gradle.shared.Person;
import org.gradle.api.PersonList;

public class TestTest {
    private String name;

    public void method() {
        FilenameUtils.separatorsToUnix("my/unix/filename");
        ToStringBuilder.reflectionToString(new Person("name"));
        new GrowthList();
        new PersonList().doSomethingWithImpl(); // compile with api-spi, runtime with api
    }

}
