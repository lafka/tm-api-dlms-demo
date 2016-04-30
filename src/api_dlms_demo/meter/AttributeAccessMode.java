package api_dlms_demo.meter;

public enum AttributeAccessMode {
    NO_ACCESS(0),
    READ_ONLY(1),
    WRITE_ONLY(2),
    READ_AND_WRITE(3),
    AUTHENTICATED_READ_ONLY(4),
    AUTHENTICATED_WRITE_ONLY(5),
    AUTHENTICATED_READ_AND_WRITE(6),

    UNKNOWN_ACCESS_MODE(-1);

    private int code;

    private AttributeAccessMode(int code) {
        this.code = code;
    }

    public static AttributeAccessMode accessModeFor(int code) {
        for (AttributeAccessMode accessMode : values()) {
            if (accessMode.code == code) {
                return accessMode;
            }
        }

        return UNKNOWN_ACCESS_MODE;
    }

}