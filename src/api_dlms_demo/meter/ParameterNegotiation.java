package api_dlms_demo.meter;

import org.openmuc.jdlms.internal.transportlayer.hdlc.FrameInvalidException;
import org.openmuc.jdlms.internal.transportlayer.hdlc.HdlcParameterNegotiation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * This class represents optional parameter that are negotiated during the connection phase between client and server on
 * the HDLC layer.
 *
 * For more information, see IEC 62056-46 section 6.4.4.4.3.2 and ISO 13239 section 5.5.3.2.2
 */
public class ParameterNegotiation extends HdlcParameterNegotiation {
    private int transmitInformationLength;
    private int receiveInformationLength;
    private int transmitWindowSize;
    private int receiveWindowSize;

    private static final int FORMAT_IDENTIFIER = 0x81;
    private static final int HDLC_PARAM_IDENTIFIER = 0x80;
    private static final int USER_PARAM_IDENTIFIER = 0xF0;

    public ParameterNegotiation(int receiveInformationLength, int receiveWindowSize) {
        super(receiveInformationLength, receiveWindowSize);
    }

    public ParameterNegotiation(int receiveInformationLength,
                                int receiveWindowSize,
                                int transmitInformationLength,
                                int transmitWindowSize) {
        super(receiveInformationLength, receiveWindowSize);

        this.transmitInformationLength = transmitInformationLength;
        this.transmitWindowSize = transmitWindowSize;
        this.receiveInformationLength = receiveInformationLength;
        this.receiveWindowSize = receiveWindowSize;
    }

    public int transmitInformationLength() {
        return transmitInformationLength;
    }

    public byte[] encode() {
        byte[] result = null;
        ByteBuffer buffer = ByteBuffer.allocate(20);


        buffer.put((byte) 0x05);
        if (transmitInformationLength > 255) {
            buffer.put((byte) 2);
            buffer.putShort((short) transmitInformationLength);
        }
        else {
            buffer.put((byte) 1);
            buffer.put((byte) transmitInformationLength);
        }


        buffer.put((byte) 0x06);
        if (receiveInformationLength > 255) {
            buffer.put((byte) 2);
            buffer.putShort((short) receiveInformationLength);
        }
        else {
            buffer.put((byte) 1);
            buffer.put((byte) receiveInformationLength);
        }


        buffer.put((byte) 0x07);
        buffer.put((byte) 4);
        buffer.putInt(transmitWindowSize);


        buffer.put((byte) 0x08);
        buffer.put((byte) 4);
        buffer.putInt(receiveWindowSize);


        int size = buffer.position();

        if (size != 0) {
            buffer.rewind();

            result = new byte[size + 3];
            result[0] = (byte) FORMAT_IDENTIFIER;
            result[1] = (byte) HDLC_PARAM_IDENTIFIER;
            result[2] = (byte) size;
            buffer.get(result, 3, size);
        }

        return result;
    }
}
