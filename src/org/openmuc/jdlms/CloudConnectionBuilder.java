package org.openmuc.jdlms;

import org.openmuc.jdlms.internal.Settings;
import org.openmuc.jdlms.internal.transportlayer.TransportLayerConnection;

import java.io.IOException;

public class CloudConnectionBuilder extends ConnectionBuilder<CloudConnectionBuilder> {
    Settings settings;
    TransportLayerConnection transport;

    public CloudConnectionBuilder(Settings settings, TransportLayerConnection transport) {
        this.settings = settings;
        this.transport = transport;

    }

    @Override
    public SnClientConnection buildSnConnection() throws IOException {
        SnClientConnection conn = new SnClientConnection(this.settings, this.transport);

        return conn;
    }

    @Override
    public LnClientConnection buildLnConnection() throws IOException {
        LnClientConnection conn = new LnClientConnection(this.settings, this.transport);

        return conn;
    }

    public static void connect(ClientConnection conn) throws IOException {
        conn.connect();
    }
}
