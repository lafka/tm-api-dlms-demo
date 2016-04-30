package api_dlms_demo.meter;

public enum MethodAccessMode {
    NO_ACCESS(0),
    ACCESS(1),
    AUTHENTICATED_ACCESS(2),

    UNKNOWN_ACCESS_MODE(-1);

    private int code;

    private MethodAccessMode(int code) {
        this.code = code;
    }

    public static MethodAccessMode accessModeFor(boolean value) {
        return accessModeFor(value ? 1 : 0);
    }

    public static MethodAccessMode accessModeFor(int code) {
        for (MethodAccessMode accessMode : values()) {
            if (accessMode.code == code) {
                return accessMode;
            }
        }

        return UNKNOWN_ACCESS_MODE;
    }
}