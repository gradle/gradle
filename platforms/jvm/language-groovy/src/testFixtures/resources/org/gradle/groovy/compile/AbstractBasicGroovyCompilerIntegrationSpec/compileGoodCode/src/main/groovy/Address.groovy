import com.google.common.base.Strings

class Address {
    String street

    String getNonNullStreet() {
        Strings.nullToEmpty(street)
    }
}
