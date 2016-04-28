package api_dlms_demo.cloud;

import clojure.lang.PersistentVector;

abstract public class CloudTransportLayerConnection implements ICloudTransportLayerConnection {
    PersistentVector state = null;
}
