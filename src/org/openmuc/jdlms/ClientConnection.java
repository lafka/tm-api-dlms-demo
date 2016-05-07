/*
 * Copyright 2012-16 Fraunhofer ISE
 *
 * This file is part of jDLMS.
 * For more information visit http://www.openmuc.org
 *
 * jDLMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * jDLMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with jDLMS.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.openmuc.jdlms;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.openmuc.jasn1.ber.types.BerObjectIdentifier;
import org.openmuc.jasn1.ber.types.BerOctetString;
import org.openmuc.jdlms.internal.APdu;
import org.openmuc.jdlms.internal.ConfirmedMode;
import org.openmuc.jdlms.internal.EncryptionSettings;
import org.openmuc.jdlms.internal.Settings;
import org.openmuc.jdlms.internal.asn1.axdr.types.AxdrBoolean;
import org.openmuc.jdlms.internal.asn1.cosem.COSEMpdu;
import org.openmuc.jdlms.internal.asn1.cosem.Conformance;
import org.openmuc.jdlms.internal.asn1.cosem.InitiateRequest;
import org.openmuc.jdlms.internal.asn1.cosem.Invoke_Id_And_Priority;
import org.openmuc.jdlms.internal.asn1.cosem.Unsigned16;
import org.openmuc.jdlms.internal.asn1.cosem.Unsigned8;
import org.openmuc.jdlms.internal.asn1.iso.acse.AARE_apdu;
import org.openmuc.jdlms.internal.asn1.iso.acse.AARQ_apdu;
import org.openmuc.jdlms.internal.asn1.iso.acse.ACSE_apdu;
import org.openmuc.jdlms.internal.asn1.iso.acse.ACSE_requirements;
import org.openmuc.jdlms.internal.asn1.iso.acse.AP_title;
import org.openmuc.jdlms.internal.asn1.iso.acse.AP_title_form2;
import org.openmuc.jdlms.internal.asn1.iso.acse.Authentication_value;
import org.openmuc.jdlms.internal.asn1.iso.acse.Mechanism_name;
import org.openmuc.jdlms.internal.security.HlsProcessorGmac;
import org.openmuc.jdlms.internal.security.HlsSecretProcessor;
import org.openmuc.jdlms.internal.transportlayer.TransportLayerConnection;
import org.openmuc.jdlms.internal.transportlayer.TransportLayerConnectionListener;

/**
 * Class used to interact with a DLMS/Cosem Server
 */
public abstract class ClientConnection implements TransportLayerConnectionListener {

    private final Settings connectionSettings;

    // private final DisconnectModule disconnectModule = new DisconnectModule();
    private final TransportLayerConnection transLayerCon;
    private Conformance negotiatedFeatures;
    private int maxSendPduSize = 0xffff;

    private final byte[] buffer;

    private final BlockingQueue<APdu> incomingResponses = new LinkedBlockingQueue<APdu>();

    private int invokeId;

    private final SecureRandom random;

    private IOException ioException;

    byte[] serverSystemTitle;

    private final EncryptionSettings encryptionSettings = new EncryptionSettings();

    ClientConnection(Settings settings, TransportLayerConnection transLayerCon) throws IOException {
        this.connectionSettings = settings;

        this.transLayerCon = transLayerCon;

        this.buffer = new byte[1000];
        this.invokeId = 1;
        this.random = new SecureRandom();

        // connect();

    }

    void connect() throws IOException {

        // settings.disableAuthentication();

        transLayerCon.startListening(this);

        int contextId = buildContextId();

        AARQ_apdu aarq = new AARQ_apdu();
        aarq.application_context_name = new BerObjectIdentifier(new int[] { 2, 16, 756, 5, 8, 1, contextId });

        HlsSecretProcessor hlsSecretProcessor = null;
        byte[] clientToServerChallenge = null;

        switch (connectionSettings.authenticationMechanism()) {
            case NONE:
                break;
            case LOW:
                setupAarqAuthentication(aarq, connectionSettings.authenticationKey());
                break;
            // case HLS3_MD5:
            // setupAarqAuthentication(aarq, connectionSettings.authenticationKey());
            // hlsSecretProcessor = new HlsProcessorMd5();
            // break;
            // case HLS4_SHA1:
            // setupAarqAuthentication(aarq, connectionSettings.authenticationKey());
            // hlsSecretProcessor = new HlsProcessorSha1();
            // break;
            case HLS5_GMAC:
                clientToServerChallenge = generateRandomSequence();
                setupAarqAuthentication(aarq, clientToServerChallenge);
                hlsSecretProcessor = new HlsProcessorGmac();
                aarq.calling_AP_title = new AP_title(new AP_title_form2(connectionSettings.systemTitle()));
                break;
            // case HLS6_SHA256:
            // setupAarqAuthentication(aarq, connectionSettings.authenticationKey());
            // hlsSecretProcessor = new HlsProcessorSha256();
            // break;
            default:
                throw new IllegalStateException("Authentication mechanism not supported.");
        }

        ACSE_apdu aarqAcseAPdu = new ACSE_apdu(aarq, null, null, null);

        COSEMpdu xDlmsInitiateRequestPdu = new COSEMpdu();
        xDlmsInitiateRequestPdu.setinitiateRequest(new InitiateRequest(null, new AxdrBoolean(confirmedModeEnabled()),
                null, new Unsigned8(6), proposedConformance(), new Unsigned16(0xFFFF)));

        APdu aarqAPdu = new APdu(aarqAcseAPdu, xDlmsInitiateRequestPdu);

        int length = aarqAPdu.encode(buffer, connectionSettings, encryptionSettings);

        // System.out.println("sending: " + aarq);

        transLayerCon.send(buffer, buffer.length - length, length);

        if (confirmedModeEnabled()) {
            connectWithEnablededConfirmedMode(hlsSecretProcessor, clientToServerChallenge);
        }
        else {
            connectWithDisabledConfirmedMode();
        }
    }

    private void setupAarqAuthentication(AARQ_apdu aarq, byte[] clientToServerChallenge) {
        aarq.mechanism_name = new Mechanism_name(
                new int[] { 2, 16, 756, 5, 8, 2, connectionSettings.authenticationMechanism().getCode() });
        aarq.sender_acse_requirements = new ACSE_requirements(new byte[] { (byte) 0x80 }, 2);
        aarq.calling_authentication_value = new Authentication_value(new BerOctetString(clientToServerChallenge), null);
    }

    protected abstract int buildContextId();

    protected boolean confirmedModeEnabled() {
        return connectionSettings.confirmedMode() == ConfirmedMode.CONFIRMED;
    }

    protected Conformance negotiatedFeatures() {
        return negotiatedFeatures;
    }

    protected int maxSendPduSize() {
        return this.maxSendPduSize;
    }

    protected Invoke_Id_And_Priority invokeIdAndPriorityFor(boolean highPriority) {

        byte[] invokeIdAndPriorityBytes = new byte[] { (byte) (invokeId & 0xF) };
        if (confirmedModeEnabled()) {
            invokeIdAndPriorityBytes[0] |= 0x40;
        }
        if (highPriority) {
            invokeIdAndPriorityBytes[0] |= 0x80;
        }

        // byte[] invokeIdAndPriorityBytes = new byte[] { (byte) (invokeId << 4) };
        // if (confirmedModeIsSet()) {
        // invokeIdAndPriorityBytes[0] |= 0x02;
        // }
        // if (highPriority) {
        // invokeIdAndPriorityBytes[0] |= 0x01;
        // }

        Invoke_Id_And_Priority result = new Invoke_Id_And_Priority(invokeIdAndPriorityBytes);

        invokeId = (invokeId + 1) % 16;
        return result;
    }

    protected void send(COSEMpdu pdu) throws IOException {

        APdu aPdu = new APdu(null, pdu);
        // System.out.println("sending: " + aPdu);
        int length = aPdu.encode(buffer, connectionSettings, encryptionSettings);

        transLayerCon.send(buffer, buffer.length - length, length);
    }

    /**
     * Disconnects connection to remote smart meter
     *
     * @param sendDisconnectMessage
     *            If a message to release the connection shall be sent to the remote client. This parameter must be true
     *            on connectionless lower layer protocols (e.g. UDP) or if you want to give the remote end point a
     *            chance to gracefully close the connection
     */
    public void disconnect(boolean sendDisconnectMessage) {
        try {
            // TODO commented out:
            // lowerLayer.removeReceivingListener(this);
            if (sendDisconnectMessage) {
                // disconnectModule.gracefulDisconnect(this); // TODO: rethink this
            }
            transLayerCon.close();
        } catch (IOException e) {
            // TODO
            // LoggingHelper.logStackTrace(e, logger);
        }
    }

    /**
     * Convenience method to call {@code get(false, params)}
     *
     * @see #get(boolean, AttributeAddress...)
     *
     * @param params
     *            Varargs of specifiers which attributes to send (See {@link AttributeAddress})
     * @return List of results from the smart meter in the same order as the requests
     * @throws IOException
     *             throws IOException
     * @throws TimeoutException
     *             if the request times out
     */
    public final List<GetResult> get(AttributeAddress... params) throws IOException, TimeoutException {
        return get(false, params);
    }

    /**
     * Requests the remote smart meter to send the values of one or several attributes
     *
     * @param highPriority
     *            if true: sends this request with high priority, if supported
     * @param params
     *            Varargs of specifiers which attributes to send (See {@link AttributeAddress})
     * @return List of results from the smart meter in the same order as the requests
     * @throws IOException
     *             if the connection breaks
     * @throws TimeoutException
     *             if the request times out
     */
    public abstract List<GetResult> get(boolean highPriority, AttributeAddress... params)
            throws IOException, TimeoutException;

    /**
     * Convenience method to call {@code set(3000, params)}
     *
     * @param params
     *            Varargs of specifier which attributes to set to which values (See {@link SetParameter})
     * @return List of results from the smart meter in the same order as the requests or null if confirmed has been set
     *         to false on creation of this object. A true value indicates that this particular value has been
     *         successfully set
     * @throws IOException
     *             throws IOException
     */
    public final List<AccessResultCode> set(SetParameter... params) throws IOException {
        return set(false, params);
    }

    /**
     * Requests the remote smart meter to set one or several attributes to the committed values
     *
     * @param params
     *            Varargs of specifier which attributes to set to which values (See {@link SetParameter})
     * @param highPriority
     *            Sends this request with high priority, if supported
     * @return List of results from the smart meter in the same order as the requests or null if confirmed has been set
     *         to false on creation of this object. A true value indicates that this particular value has been
     *         successfully set
     * @throws IOException
     *             throws IOException
     */
    public abstract List<AccessResultCode> set(boolean highPriority, SetParameter... params) throws IOException;

    /**
     *
     * Convenience method to call {@code action(false, params)}
     *
     * @param params
     *            List of specifier which methods to be called and, if needed, what parameters to call (See
     *            {@link MethodParameter}
     * @return List of results from the smart meter in the same order as the requests or null if confirmed has been set
     *         to false on creation of this object
     * @throws IOException
     *             throws IOException
     */
    public final List<MethodResult> action(MethodParameter... params) throws IOException {
        return action(false, params);
    }

    /**
     * Requests the remote smart meter to call one or several methods with or without committed parameters
     *
     * @param params
     *            List of specifier which methods to be called and, if needed, what parameters to call (See
     *            {@link MethodParameter}
     * @param highPriority
     *            Sends this request with high priority, if supported
     * @return List of results from the smart meter in the same order as the requests or null if confirmed has been set
     *         to false on creation of this object
     * @throws IOException
     *             if the connection breaks, while requesting
     */
    public abstract List<MethodResult> action(boolean highPriority, MethodParameter... params) throws IOException;

    /**
     * Convenience method to call {@code disconnect(true)}
     *
     * @see #disconnect(boolean)
     */
    public void disconnect() {
        disconnect(true);
    }

    public void close() {
        disconnect(false);
    }

    @Override
    public void dataReceived(byte[] data) {
        System.out.println("ClientConnection got input data!");
        APdu aPdu;
        try {
            aPdu = new APdu(new DataInputStream(new ByteArrayInputStream(data)), this.connectionSettings,
                    data[0] & 0xff, encryptionSettings);
        } catch (IOException e) {
            System.out.println("Got IOException: " + e.getMessage());
            ioException = e;
            try {
                incomingResponses.put(new APdu(null, null));
            } catch (InterruptedException e1) {
                System.out.println("Got InterruptedException/1: " + e1.getMessage());
            }
            return;
        }
        // System.out.println("received: " + aPdu);

        try {
            if (aPdu.acseAPdu != null) {
                incomingResponses.put(aPdu);
            }
            else {
                processPdu(aPdu.cosemPdu);
            }
        } catch (InterruptedException e) {
            System.out.println("Got InterruptedException/2: " + e.getMessage());
        }

    }

    @Override
    public void connectionInterrupted(IOException e) {
        ioException = e;
        try {
            incomingResponses.put(new APdu(null, null));
        } catch (InterruptedException e1) {
        }

        // FIXME: repair this.
        // if (connectionSettings.clientConnectionEventListener() != null) {
        // connectionSettings.clientConnectionEventListener().connectionClosed(e);
        // }
    }

    /**
     * Change the global authentication key used by the client.
     *
     * @param key
     *            the new key
     */
    public void changeClientGlobalAuthenticationKey(byte[] key) {
        connectionSettings.authenticationKey(key);
    }

    /**
     * Change the global encryption used by the client.
     *
     * @param key
     *            the new key
     */
    public void changeClientGlobalEncryptionKey(byte[] key) {
        connectionSettings.globalEncryptionKey(key);
    }

    protected abstract Conformance proposedConformance();

    protected abstract void processPdu(COSEMpdu pdu);

    protected abstract void validateReferencingMethod() throws IOException;

    protected abstract byte[] hlsAuthentication(byte[] processedChallenge) throws IOException;

    private void connectWithEnablededConfirmedMode(HlsSecretProcessor hlsSecretProcessor,
                                                   byte[] clientToServerChallenge) throws IOException {
        APdu decodedResponsePdu = retrieveServerResponsePdu();

        try {
            validate(decodedResponsePdu);
        } catch (EOFException e) {
            throw new IOException("Connection closed by remote host while waiting for association response (AARE).", e);
        } catch (IOException e) {
            throw new IOException("Error while receiving association response: " + e.getMessage() + ".", e);
        }

        AARE_apdu aare = decodedResponsePdu.acseAPdu.aare;

        if (aare.result.value != 0) {
            transLayerCon.close();
            String errorMsg;
            if (aare.result_source_diagnostic.acse_service_user != null) {
                errorMsg = "ACSE service user = " + aare.result_source_diagnostic.acse_service_user.value;
            }
            else {
                errorMsg = "ACSE service provider = " + aare.result_source_diagnostic.acse_service_provider.value;
            }

            throw new IOException("Error on establishing connection. Error code from AARE message: " + errorMsg);
        }

        COSEMpdu xDlmsInitResponse = decodedResponsePdu.cosemPdu;

        this.maxSendPduSize = (int) xDlmsInitResponse.initiateResponse.server_max_receive_pdu_size.getValue();
        this.negotiatedFeatures = xDlmsInitResponse.initiateResponse.negotiated_conformance;

        validateReferencingMethod();

        // Step 3 and 4 of HLS
        if (connectionSettings.authenticationMechanism().getCode() > 1) {

            byte[] serverToClientChallenge = aare.responding_authentication_value.charstring.value;
            byte[] processedChallenge;
            byte[] remoteResponse;
            byte[] frameCounter = new byte[4];
            int frameCounterInt;

            processedChallenge = hlsSecretProcessor.process(serverToClientChallenge,
                    connectionSettings.authenticationKey(), connectionSettings.globalEncryptionKey(),
                    connectionSettings.systemTitle(), encryptionSettings.frameCounter);

            try {
                remoteResponse = hlsAuthentication(processedChallenge);
            } catch (IOException e) {
                throw new IOException("Exception during HLS authentication steps 3 and 4.", e);
            }

            if (remoteResponse == null) {
                throw new IOException("Got no remote response challenge for HLS authentication steps 4.");
            }

            System.arraycopy(remoteResponse, 1, frameCounter, 0, 4);
            frameCounterInt = ByteBuffer.wrap(frameCounter).getInt();
            processedChallenge = hlsSecretProcessor.process(clientToServerChallenge,
                    connectionSettings.authenticationKey(), connectionSettings.globalEncryptionKey(),
                    aare.responding_AP_title.ap_title_form2.value, frameCounterInt);

            validate(processedChallenge, remoteResponse);
        }
    }

    private void validate(byte[] processedChallenge, byte[] remoteResponse) throws IOException {
        if (remoteResponse == null) {
            throw new IOException("Authentication failed");
        }

        if (!Arrays.equals(remoteResponse, processedChallenge)) {
            throw new IOException("Server wasn't able to authenticate itself");
        }
    }

    private APdu retrieveServerResponsePdu() throws IllegalStateException {
        try {
            if (connectionSettings.responseTimeout() == 0) {
                return incomingResponses.take();
            }
            else {
                return incomingResponses.poll(connectionSettings.responseTimeout(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private void validate(APdu decodedResponsePdu) throws IOException {
        if (decodedResponsePdu == null) {
            if (ioException != null) {
                throw ioException;
            }
            throw new IOException("Timeout waiting for associate response message (AARE). No further information.");
        }

        if (decodedResponsePdu.cosemPdu == null) {
            throw ioException;
        }

        if (decodedResponsePdu.acseAPdu == null || decodedResponsePdu.acseAPdu.aare == null) {
            throw new IOException("Did not receive expected associate response (AARE) message.");
        }
    }

    private void connectWithDisabledConfirmedMode() throws IOException {
        this.maxSendPduSize = 0xffff;
        this.negotiatedFeatures = proposedConformance();

        validateReferencingMethod();
    }

    private byte[] generateRandomSequence() {

        int resultLength = connectionSettings.challengeLength();
        byte[] result = new byte[resultLength];

        for (int i = 0; i < resultLength; i++) {
            byte[] resultByte = new byte[1];

            // Only allow printable characters
            do {
                random.nextBytes(resultByte);
            } while (resultByte[0] >= 0 && resultByte[0] <= 31);

            result[i] = resultByte[0];
        }

        return result;
    }

    protected Settings connectionSettings() {
        return this.connectionSettings;
    }

}
