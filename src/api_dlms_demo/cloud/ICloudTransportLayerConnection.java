package api_dlms_demo.cloud;

import clojure.lang.PersistentVector;
import org.openmuc.jdlms.internal.transportlayer.TransportLayerConnection;
import org.openmuc.jdlms.internal.transportlayer.hdlc.HdlcFrame;

/**
 * Created by user on 4/24/16.
 */
public interface ICloudTransportLayerConnection extends TransportLayerConnection {
    PersistentVector state = null;
    void send(byte[] buf);
    void sendraw(byte[] buf);
    HdlcFrame poll();
}
