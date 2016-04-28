package api_dlms_demo.cloud;

import org.openmuc.jdlms.internal.AuthenticationMechanism;
import org.openmuc.jdlms.internal.ConfirmedMode;
import org.openmuc.jdlms.internal.Settings;
import org.openmuc.jdlms.internal.security.DataTransmissionLevel;

public class DLMSSettings implements Settings {
    private final AuthenticationMechanism authenticationMechanism;
    private byte[] authenticationKey;
    private final ConfirmedMode confirmedMode;
    private byte[] globalEncryptionKey;
    private final byte[] systemTitle;
    private final int challengeLength;
    private final long deviceId;
    private final int responseTimeout;
    private final int messageFragmentTimeout;
    private final String manufactureId;
    private final DataTransmissionLevel dataTransmissionLevel;
    private final int clientAccessPoint;
    private final int logicalDeviceAddress;

    public DLMSSettings(
            AuthenticationMechanism authenticationMechanism,
            byte[] authenticationKey,
            ConfirmedMode confirmedMode,
            byte[] globalEncryptionKey,
            byte[] systemTitle,
            int challengeLength,
            long deviceId,
            int responseTimeout,
            int messageFragmentTimeout,
            String manufactureId,
            DataTransmissionLevel dataTransmissionLevel,
            int clientAccessPoint,
            int logicalDeviceAddress
    ) {

        this.authenticationMechanism = authenticationMechanism;
        this.confirmedMode = confirmedMode;
        this.authenticationKey = authenticationKey;
        this.globalEncryptionKey = globalEncryptionKey;
        this.systemTitle = systemTitle;
        this.challengeLength = challengeLength;
        this.deviceId = deviceId;
        this.responseTimeout = responseTimeout;
        this.messageFragmentTimeout = messageFragmentTimeout;
        this.manufactureId = manufactureId;
        this.dataTransmissionLevel = dataTransmissionLevel;
        this.clientAccessPoint = clientAccessPoint;
        this.logicalDeviceAddress = logicalDeviceAddress;
    }

    @Override
    public AuthenticationMechanism authenticationMechanism() {
        return this.authenticationMechanism;
    }

    @Override
    public byte[] authenticationKey() {
        return this.authenticationKey;
    }

    @Override
    public byte[] globalEncryptionKey() {
        return this.globalEncryptionKey;
    }

    @Override
    public int challengeLength() {
        return this.challengeLength;
    }

    @Override
    public byte[] systemTitle() {
        return this.systemTitle;
    }

    @Override
    public long deviceId() {
        return this.deviceId;
    }

    @Override
    public int responseTimeout() {
        return this.responseTimeout;
    }

    @Override
    public int messageFragmentTimeout() {
        return this.messageFragmentTimeout;
    }

    @Override
    public String manufactureId() {
        return this.manufactureId;
    }

    @Override
    public ConfirmedMode confirmedMode() {
        return this.confirmedMode;
    }

    @Override
    public DataTransmissionLevel dataTransmissionLevel() {
        return this.dataTransmissionLevel;
    }

    @Override
    public int logicalDeviceAddress() {
        return this.logicalDeviceAddress;
    }

    @Override
    public int clientAccessPoint() {
        return this.clientAccessPoint;
    }

    @Override
    public void authenticationKey(byte[] authenticationKey) {
        this.authenticationKey = authenticationKey;
    }

    @Override
    public void globalEncryptionKey(byte[] globalEncryptionKey) {
        this.globalEncryptionKey = globalEncryptionKey;
    }
}
