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

import static org.openmuc.jdlms.internal.PduHelper.invokeIdFrom;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.openmuc.jdlms.datatypes.DataObject;
import org.openmuc.jdlms.interfaceclass.method.AssociationLnMethod;
import org.openmuc.jdlms.internal.ConformanceHelper;
import org.openmuc.jdlms.internal.DataConverter;
import org.openmuc.jdlms.internal.PduHelper;
import org.openmuc.jdlms.internal.Settings;
import org.openmuc.jdlms.internal.asn1.axdr.AxdrType;
import org.openmuc.jdlms.internal.asn1.axdr.NullOutputStream;
import org.openmuc.jdlms.internal.asn1.axdr.types.AxdrEnum;
import org.openmuc.jdlms.internal.asn1.cosem.ACTION_Request;
import org.openmuc.jdlms.internal.asn1.cosem.ACTION_Response;
import org.openmuc.jdlms.internal.asn1.cosem.Action_Request_Next_Pblock;
import org.openmuc.jdlms.internal.asn1.cosem.Action_Request_Normal;
import org.openmuc.jdlms.internal.asn1.cosem.Action_Request_With_List;
import org.openmuc.jdlms.internal.asn1.cosem.Action_Response_With_Optional_Data;
import org.openmuc.jdlms.internal.asn1.cosem.COSEMpdu;
import org.openmuc.jdlms.internal.asn1.cosem.Conformance;
import org.openmuc.jdlms.internal.asn1.cosem.Cosem_Attribute_Descriptor;
import org.openmuc.jdlms.internal.asn1.cosem.Cosem_Attribute_Descriptor_With_Selection;
import org.openmuc.jdlms.internal.asn1.cosem.Cosem_Method_Descriptor;
import org.openmuc.jdlms.internal.asn1.cosem.Cosem_Object_Instance_Id;
import org.openmuc.jdlms.internal.asn1.cosem.GET_Request;
import org.openmuc.jdlms.internal.asn1.cosem.GET_Response;
import org.openmuc.jdlms.internal.asn1.cosem.Get_Data_Result;
import org.openmuc.jdlms.internal.asn1.cosem.Get_Request_Next;
import org.openmuc.jdlms.internal.asn1.cosem.Get_Request_Normal;
import org.openmuc.jdlms.internal.asn1.cosem.Get_Request_With_List;
import org.openmuc.jdlms.internal.asn1.cosem.Integer8;
import org.openmuc.jdlms.internal.asn1.cosem.Invoke_Id_And_Priority;
import org.openmuc.jdlms.internal.asn1.cosem.SET_Request;
import org.openmuc.jdlms.internal.asn1.cosem.SET_Response;
import org.openmuc.jdlms.internal.asn1.cosem.Selective_Access_Descriptor;
import org.openmuc.jdlms.internal.asn1.cosem.Set_Request_Normal;
import org.openmuc.jdlms.internal.asn1.cosem.Set_Request_With_List;
import org.openmuc.jdlms.internal.asn1.cosem.Unsigned16;
import org.openmuc.jdlms.internal.asn1.cosem.Unsigned8;
import org.openmuc.jdlms.internal.security.DataTransmissionLevel;
import org.openmuc.jdlms.internal.transportlayer.TransportLayerConnection;

/**
 * Variant of the connection class using decrypted messages with logical name referencing to communicate with the remote
 * smart meter
 */
public class LnClientConnection2 extends ClientConnection {

    // Allow GET/SET/ACTION/EVENT
    // Allow selective access
    // Allow priority
    // Allow multiple references
    // Allow block transfer
    // Allow attribute 0 GET/SET
    /**
     * Bit field containing all operations this client can perform
     */
    private static final Conformance PROPOSED_CONFORMANCE = new Conformance(
            new byte[] { (byte) 0x00, (byte) 0xBC, (byte) 0x3F }, 24);

    private final ResponseQueue<ACTION_Response> actionResponseQueue = new ResponseQueue<ACTION_Response>();
    private final ResponseQueue<GET_Response> getResponseQueue = new ResponseQueue<GET_Response>();
    private final ResponseQueue<SET_Response> setResponseQueue = new ResponseQueue<SET_Response>();

    LnClientConnection2(Settings settings, TransportLayerConnection transportCon) throws IOException {
        super(settings, transportCon);
    }

    @Override
    public List<GetResult> get(boolean highPriority, AttributeAddress... params) throws IOException, TimeoutException {

        Invoke_Id_And_Priority id = invokeIdAndPriorityFor(highPriority);
        final int invokeId = (id.getValue()[0] & 0xF);
        COSEMpdu pdu = createGetPdu(id, params);
        send(pdu);

        System.out.println("waiting for a response");
        GET_Response response = getResponseQueue.poll(invokeId, connectionSettings().responseTimeout());

        List<GetResult> result = new ArrayList<GetResult>(params.length);
        if (response.getChoiceIndex() == GET_Response.Choices.GET_RESPONSE_NORMAL) {
            GetResult res = convertPduToGetResult(response.get_response_normal.result);
            result.add(res);
        }
        else if (response.getChoiceIndex() == GET_Response.Choices.GET_RESPONSE_WITH_DATABLOCK) {
            GET_Request getRequest = new GET_Request();
            ByteArrayOutputStream datablocks = new ByteArrayOutputStream();
            Get_Request_Next nextBlock = new Get_Request_Next();
            nextBlock.invoke_id_and_priority = response.get_response_with_datablock.invoke_id_and_priority;
            while (response.get_response_with_datablock.result.last_block.getValue() == false) {
                datablocks.write(response.get_response_with_datablock.result.result.raw_data.getValue());

                nextBlock.block_number = response.get_response_with_datablock.result.block_number;
                getRequest.setget_request_next(nextBlock);
                pdu.setget_request(getRequest);
                send(pdu);

                try {
                    response = getResponseQueue.poll(invokeId, connectionSettings().responseTimeout());
                } catch (TimeoutException e) {
                    // Send PDU with wrong block number to indicate the
                    // device that the block transfer is
                    // aborted.
                    // This is the well defined behavior to abort a block
                    // transfer as in IEC 62056-53 section
                    // 7.4.1.8.2
                    // receiveTimedOut(pdu);
                    send(pdu);
                    throw new IOException(e);
                }
            }

            // last block is not a datablock but rather data_access_result
            if (null != response.get_response_with_datablock.result.result.raw_data) {
                datablocks.write(response.get_response_with_datablock.result.result.raw_data.getValue());
            }

            InputStream dataByteStream = new ByteArrayInputStream(datablocks.toByteArray());
            while (dataByteStream.available() > 0) {
                org.openmuc.jdlms.internal.asn1.cosem.Data resultPduData = new org.openmuc.jdlms.internal.asn1.cosem.Data();
                resultPduData.decode(dataByteStream);
                Get_Data_Result getResult = new Get_Data_Result();
                getResult.setdata(resultPduData);
                GetResult res = convertPduToGetResult(getResult);
                result.add(res);
            }
        }
        else if (response.getChoiceIndex() == GET_Response.Choices.GET_RESPONSE_WITH_LIST) {
            for (Get_Data_Result resultPdu : response.get_response_with_list.result.list()) {
                GetResult res = convertPduToGetResult(resultPdu);
                result.add(res);
            }
        }
        else {
            throw new IllegalStateException(String.format(
                    "Unknown response type with Choise Index %s. Please report to developer of the stack.",
                    response.getChoiceIndex()));
        }

        return result;
    }

    private GetResult convertPduToGetResult(Get_Data_Result pdu) {
        if (pdu.getChoiceIndex() == Get_Data_Result.Choices.DATA) {
            return new GetResult(DataConverter.toApi(pdu.data));
        }
        else {
            return new GetResult(AccessResultCode.forValue((int) pdu.data_access_result.getValue()));
        }
    }

    @Override
    public List<AccessResultCode> set(boolean highPriority, SetParameter... params) throws IOException {
        Invoke_Id_And_Priority invokeIdAndPriority = invokeIdAndPriorityFor(highPriority);
        int invokeId = invokeIdFrom(invokeIdAndPriority);
        List<COSEMpdu> pdus = createSetPdu(invokeIdAndPriority, params);
        send(pdus.remove(0));

        if (!confirmedModeEnabled()) {
            return null;
        }

        SET_Response response;
        try {
            response = setResponseQueue.poll(invokeId, connectionSettings().responseTimeout());
        } catch (TimeoutException e) {
            throw new IOException("Interrupted while waiting for incoming response", e);
        }

        while (response.getChoiceIndex() == SET_Response.Choices.SET_RESPONSE_DATABLOCK) {
            send(pdus.remove(0));
            try {
                response = setResponseQueue.poll(invokeId, connectionSettings().responseTimeout());
            } catch (TimeoutException e) {
                throw new IOException("Interrupted while waiting for incoming response", e);
            }
        }

        switch (response.getChoiceIndex()) {
            case SET_RESPONSE_NORMAL:
                return axdrEnumToAccessResultCode(response.set_response_normal.result);

            case SET_RESPONSE_WITH_LIST:
                return axdrEnumsToAccessResultCodes(response.set_response_with_list.result.list());

            case SET_RESPONSE_LAST_DATABLOCK:
                return axdrEnumToAccessResultCode(response.set_response_last_datablock.result);

            case SET_RESPONSE_LAST_DATABLOCK_WITH_LIST:
                return axdrEnumsToAccessResultCodes(response.set_response_last_datablock_with_list.result.list());

            default:
                throw new IllegalStateException("Unknown response type");
        }

    }

    private List<AccessResultCode> axdrEnumToAccessResultCode(AxdrEnum axdrEnum) {
        List<AccessResultCode> result = new ArrayList<>(1);
        result.add(AccessResultCode.forValue(axdrEnum.getValue()));
        return result;
    }

    private List<AccessResultCode> axdrEnumsToAccessResultCodes(List<AxdrEnum> enums) {
        List<AccessResultCode> result = new ArrayList<>(enums.size());
        for (AxdrEnum res : enums) {
            result.add(AccessResultCode.forValue(res.getValue()));
        }
        return result;
    }

    @Override
    public List<MethodResult> action(boolean highPriority, MethodParameter... params) throws IOException {
        Invoke_Id_And_Priority id = invokeIdAndPriorityFor(highPriority);
        int invokeId = invokeIdFrom(id);
        List<COSEMpdu> pdus = createActionPdu(id, params);

        send(pdus.remove(0));

        List<MethodResult> result = null;
        if (confirmedModeEnabled()) {
            ACTION_Response response;
            try {
                response = actionResponseQueue.poll(invokeId, connectionSettings().responseTimeout());
            } catch (TimeoutException e) {
                throw new IOException("Interrupted while waiting for incoming response", e);
            }

            while (response.getChoiceIndex() == ACTION_Response.Choices.ACTION_RESPONSE_NEXT_PBLOCK) {
                send(pdus.remove(0));

                try {
                    response = actionResponseQueue.poll(invokeId, connectionSettings().responseTimeout());
                } catch (TimeoutException e) {
                    throw new IOException("Interrupted while waiting for incoming response", e);
                }
            }

            result = new ArrayList<MethodResult>(params.length);
            if (response.getChoiceIndex() == ACTION_Response.Choices.ACTION_RESPONSE_NORMAL) {
                Action_Response_With_Optional_Data resp = response.action_response_normal.single_response;
                DataObject resultData = null;

                if (resp.return_parameters.isUsed()) {
                    resultData = DataConverter.toApi(resp.return_parameters.getValue().data);
                }
                result.add(new MethodResult(MethodResultCode.methodResultCodeFor((int) resp.result.getValue()),
                        resultData));
            }
            else if (response.getChoiceIndex() == ACTION_Response.Choices.ACTION_RESPONSE_WITH_LIST) {
                for (Action_Response_With_Optional_Data resp : response.action_response_with_list.list_of_responses
                        .list()) {
                    DataObject resultData = null;
                    if (resp.return_parameters.isUsed()) {
                        resultData = DataConverter.toApi(resp.return_parameters.getValue().data);
                    }
                    result.add(new MethodResult(MethodResultCode.methodResultCodeFor((int) resp.result.getValue()),
                            resultData));
                }
            }
            else if (response.getChoiceIndex() == ACTION_Response.Choices.ACTION_RESPONSE_WITH_PBLOCK) {
                ByteArrayOutputStream datablocks = new ByteArrayOutputStream();
                COSEMpdu pdu = new COSEMpdu();
                ACTION_Request request = new ACTION_Request();
                Action_Request_Next_Pblock nextBlock = new Action_Request_Next_Pblock();
                nextBlock.invoke_id_and_priority = response.action_response_with_pblock.invoke_id_and_priority;
                while (response.action_response_with_pblock.pblock.last_block.getValue() == false) {
                    datablocks.write(response.action_response_with_pblock.pblock.raw_data.getValue());

                    nextBlock.block_number = response.action_response_with_pblock.pblock.block_number;
                    request.setaction_request_next_pblock(nextBlock);
                    pdu.setaction_request(request);
                    send(pdu);

                    try {
                        response = actionResponseQueue.poll(invokeId, connectionSettings().responseTimeout());
                    } catch (TimeoutException e) {
                        throw new IOException("Interrupted while waiting for incoming response", e);
                    }
                }
                datablocks.write(response.action_response_with_pblock.pblock.raw_data.getValue());
                InputStream dataByteStream = new ByteArrayInputStream(datablocks.toByteArray());
                while (dataByteStream.available() > 0) {
                    Get_Data_Result dataResult = new Get_Data_Result();
                    dataResult.decode(dataByteStream);
                    // If remote Method call returns a pdu that must be
                    // segmented into datablocks, we can assume that the call
                    // was successful.
                    DataObject resultData = DataConverter.toApi(dataResult.data);
                    result.add(new MethodResult(MethodResultCode.SUCCESS, resultData));
                }
            }
            else {
                throw new UnsupportedOperationException("Unknown response type");
            }
        }

        return result;
    }

    @Override
    protected void processPdu(COSEMpdu pdu) {
        System.out.println("COSEMpdu -> " + pdu);

        try {
            switch (pdu.getChoiceIndex()) {
                case GET_RESPONSE:
                    getResponseQueue.put(PduHelper.invokeIdFrom(pdu.get_response), pdu.get_response);
                    break;
                case SET_RESPONSE:
                    setResponseQueue.put(PduHelper.invokeIdFrom(pdu.set_response), pdu.set_response);
                    break;
                case ACTION_RESPONSE:
                    actionResponseQueue.put(PduHelper.invokeIdFrom(pdu.action_response), pdu.action_response);
                    break;
                case EVENT_NOTIFICATION_REQUEST:
                    // FIXME: fix this. check how DLMS event listening works
                    // if (connectionSettings().clientConnectionEventListener() != null) {
                    // EventNotification notification = DataConverter.toApi(pdu.event_notification_request);
                    // connectionSettings().clientConnectionEventListener().onEventReceived(notification);
                    // }
                    break;

                default:
                    // TODO: handle this case..
            }
        } catch (InterruptedException e) {
            System.out.println("APDU/EXCEPTION");
            // TODO
            // LoggingHelper.logStackTrace(e, logger);
        }
    }

    @Override
    protected Conformance proposedConformance() {
        return PROPOSED_CONFORMANCE;
    }

    @Override
    protected byte[] hlsAuthentication(byte[] processedChallenge) throws IOException {
        DataObject param = DataObject.newOctetStringData(processedChallenge);

        MethodParameter authenticate = new MethodParameter(AssociationLnMethod.REPLY_TO_HLS_AUTHENTICATION,
                new ObisCode(0, 0, 40, 0, 0, 255), param);

        List<MethodResult> result = action(true, authenticate);

        if (result.get(0).resultCode() == MethodResultCode.SUCCESS) {
            return result.get(0).resultData().value();
        }
        else {
            return null;
        }
    }

    /*
     * Creates a PDU to read all attributes listed in params
     */
    private COSEMpdu createGetPdu(Invoke_Id_And_Priority id, AttributeAddress... params) {
        if (params == null || params.length == 0) {
            throw new IllegalArgumentException("No parameter provided for get");
        }
        if (!ConformanceHelper.isAttribute0GetAllowed(negotiatedFeatures())) {
            for (AttributeAddress param : params) {
                if (param.attributeId() == 0) {
                    throw new IllegalArgumentException("No Attribute 0 on get allowed");
                }
            }
        }
        if (!ConformanceHelper.isSelectiveAccessAllowed(proposedConformance())) {
            for (AttributeAddress param : params) {
                if (param.accessSelection() != null) {
                    throw new IllegalArgumentException("Selective Access not supported on this connection");
                }
            }
        }

        GET_Request getRequest = new GET_Request();
        if (params.length == 1) {
            Get_Request_Normal requestNormal = new Get_Request_Normal();
            requestNormal.invoke_id_and_priority = id;
            requestNormal.cosem_attribute_descriptor = new Cosem_Attribute_Descriptor(
                    new Unsigned16(params[0].classId()), new Cosem_Object_Instance_Id(params[0].obisCode().bytes()),
                    new Integer8(params[0].attributeId()));
            SelectiveAccessDescription accessSelection = params[0].accessSelection();
            if (accessSelection != null) {
                requestNormal.access_selection
                        .setValue(new Selective_Access_Descriptor(new Unsigned8(accessSelection.accessSelector()),
                                DataConverter.toPdu(accessSelection.accessParameter())));
            }

            getRequest.setget_request_normal(requestNormal);
        }
        else {
            Get_Request_With_List requestList = new Get_Request_With_List();
            requestList.invoke_id_and_priority = id;
            requestList.attribute_descriptor_list = new Get_Request_With_List.SubSeqOf_attribute_descriptor_list();
            for (AttributeAddress p : params) {
                Selective_Access_Descriptor access = null;
                SelectiveAccessDescription accessSelection = p.accessSelection();
                if (accessSelection != null) {
                    access = new Selective_Access_Descriptor(new Unsigned8(accessSelection.accessSelector()),
                            DataConverter.toPdu(accessSelection.accessParameter()));
                }
                requestList.attribute_descriptor_list.add(new Cosem_Attribute_Descriptor_With_Selection(
                        new Cosem_Attribute_Descriptor(new Unsigned16(p.classId()),
                                new Cosem_Object_Instance_Id(p.obisCode().bytes()), new Integer8(p.attributeId())),
                        access));
            }

            getRequest.setget_request_with_list(requestList);
        }

        COSEMpdu pdu = new COSEMpdu();
        pdu.setget_request(getRequest);

        return pdu;
    }

    private int pduSizeOf(AxdrType pdu) {
        try {
            return pdu.encode(new NullOutputStream());
        } catch (IOException e) {
            return 0;
        }
    }

    private List<COSEMpdu> createSetPdu(Invoke_Id_And_Priority id, SetParameter[] params) throws IOException {
        if (params == null || params.length == 0) {
            throw new IllegalArgumentException("No parameter provided for set");
        }
        if (!ConformanceHelper.isAttribute0SetAllowed(negotiatedFeatures())) {
            for (SetParameter param : params) {
                if (param.attributeAddress().attributeId() == 0) {
                    throw new IllegalArgumentException("No Attribute 0 on set allowed");
                }
            }
        }

        List<COSEMpdu> result = new LinkedList<COSEMpdu>();

        SET_Request request = new SET_Request();
        COSEMpdu pdu = null;

        if (params.length == 1) {
            Set_Request_Normal requestNormal = new Set_Request_Normal();
            requestNormal.invoke_id_and_priority = id;
            requestNormal.cosem_attribute_descriptor = new Cosem_Attribute_Descriptor(
                    new Unsigned16(params[0].attributeAddress().classId()),
                    new Cosem_Object_Instance_Id(params[0].attributeAddress().obisCode().bytes()),
                    new Integer8(params[0].attributeAddress().attributeId()));
            requestNormal.value = DataConverter.toPdu(params[0].data());
            SelectiveAccessDescription accessSelection = params[0].attributeAddress().accessSelection();
            if (accessSelection != null) {
                requestNormal.access_selection
                        .setValue(new Selective_Access_Descriptor(new Unsigned8(accessSelection.accessSelector()),
                                DataConverter.toPdu(accessSelection.accessParameter())));
            }
            request.setset_request_normal(requestNormal);
        }
        else {
            Set_Request_With_List requestList = new Set_Request_With_List();
            requestList.invoke_id_and_priority = id;
            requestList.attribute_descriptor_list = new Set_Request_With_List.SubSeqOf_attribute_descriptor_list();
            requestList.value_list = new Set_Request_With_List.SubSeqOf_value_list();
            for (SetParameter p : params) {
                Selective_Access_Descriptor access = null;
                SelectiveAccessDescription accessSelection = p.attributeAddress().accessSelection();
                if (accessSelection != null) {
                    access = new Selective_Access_Descriptor(new Unsigned8(accessSelection.accessSelector()),
                            DataConverter.toPdu(accessSelection.accessParameter()));
                }
                Cosem_Attribute_Descriptor desc = new Cosem_Attribute_Descriptor(
                        new Unsigned16(p.attributeAddress().classId()),
                        new Cosem_Object_Instance_Id(p.attributeAddress().obisCode().bytes()),
                        new Integer8(p.attributeAddress().attributeId()));
                requestList.attribute_descriptor_list.add(new Cosem_Attribute_Descriptor_With_Selection(desc, access));
                requestList.value_list.add(DataConverter.toPdu(p.data()));
            }
            request.setset_request_with_list(requestList);
        }

        if (pduSizeOf(request) < maxSendPduSize()) {
            pdu = new COSEMpdu();
            pdu.setset_request(request);
            result.add(pdu);
        }
        else {

            // if (params.length == 1) {
            // baos.reset();
            // request.set_request_normal.value.encode(baos);
            // dataBuffer = ByteBuffer.wrap(baos.getArray());
            //
            // Set_Request_With_First_Datablock requestFirstBlock = new Set_Request_With_First_Datablock();
            // requestFirstBlock.invoke_id_and_priority = id;
            // requestFirstBlock.cosem_attribute_descriptor = request.set_request_normal.cosem_attribute_descriptor;
            // requestFirstBlock.access_selection = request.set_request_normal.access_selection;
            // requestFirstBlock.datablock = new DataBlock_SA(new AxdrBoolean(false), new Unsigned32(0),
            // new AxdrOctetString(0));
            //
            // // TODO what is baos used here for?
            // baos.reset();
            // int length = requestFirstBlock.encode(baos);
            // byte[] firstDataChunk = new byte[maxSendPduSize() - 2 - length];
            // dataBuffer.get(firstDataChunk, 0, firstDataChunk.length);
            // requestFirstBlock.datablock.raw_data = new AxdrOctetString(firstDataChunk);
            //
            // request.setset_request_with_first_datablock(requestFirstBlock);
            // pdu = new COSEMpdu();
            // pdu.setset_request(request);
            // result.add(pdu);
            // }
            // else {
            // baos.reset();
            // for (int i = request.set_request_with_list.value_list.size() - 1; i >= 0; i--) {
            // request.set_request_with_list.value_list.get(i).encode(baos);
            // }
            // dataBuffer = ByteBuffer.wrap(baos.getArray());
            //
            // Set_Request_With_List_And_First_Datablock requestListFirstBlock = new
            // Set_Request_With_List_And_First_Datablock();
            // requestListFirstBlock.invoke_id_and_priority = id;
            // requestListFirstBlock.attribute_descriptor_list = new
            // Set_Request_With_List_And_First_Datablock.SubSeqOf_attribute_descriptor_list();
            // requestListFirstBlock.datablock = new DataBlock_SA(new AxdrBoolean(false), new Unsigned32(1),
            // new AxdrOctetString(0));
            //
            // for (Cosem_Attribute_Descriptor_With_Selection desc :
            // request.set_request_with_list.attribute_descriptor_list
            // .list()) {
            // requestListFirstBlock.attribute_descriptor_list.add(desc);
            // }
            //
            // baos.reset();
            // int length = requestListFirstBlock.encode(baos);
            // byte[] firstDataChunk = new byte[maxSendPduSize() - 2 - length];
            // dataBuffer.get(firstDataChunk, 0, firstDataChunk.length);
            // requestListFirstBlock.datablock.raw_data = new AxdrOctetString(firstDataChunk);
            //
            // request.setset_request_with_list_and_first_datablock(requestListFirstBlock);
            // pdu = new COSEMpdu();
            // pdu.setset_request(request);
            // result.add(pdu);
            // }
            //
            // int blockNr = 1;
            // while (dataBuffer.hasRemaining()) {
            // blockNr++;
            // int blockLength = Math.min(maxSendPduSize() - 9, dataBuffer.remaining());
            // byte[] dataBlock = new byte[blockLength];
            // dataBuffer.get(dataBlock, 0, dataBlock.length);
            //
            // Set_Request_With_Datablock requestBlock = new Set_Request_With_Datablock();
            // requestBlock.invoke_id_and_priority = id;
            // requestBlock.datablock = new DataBlock_SA(new AxdrBoolean(dataBuffer.remaining() == 0),
            // new Unsigned32(blockNr), new AxdrOctetString(dataBlock));
            //
            // request.setset_request_with_datablock(requestBlock);
            // pdu = new COSEMpdu();
            // pdu.setset_request(request);
            // result.add(pdu);
            // }
        }

        return result;
    }

    private List<COSEMpdu> createActionPdu(Invoke_Id_And_Priority id, MethodParameter... params) throws IOException {
        if (params == null || params.length == 0) {
            throw new IllegalArgumentException("No parameter provided for set");
        }
        for (MethodParameter param : params) {
            if (param.methodId() == 0) {
                throw new IllegalArgumentException("MethodID 0 not allowed on action");
            }
        }

        List<COSEMpdu> result = new LinkedList<COSEMpdu>();

        ACTION_Request request = new ACTION_Request();
        COSEMpdu pdu = null;

        if (params.length == 1) {
            Action_Request_Normal requestNormal = new Action_Request_Normal();
            requestNormal.invoke_id_and_priority = id;
            requestNormal.cosem_method_descriptor = new Cosem_Method_Descriptor(new Unsigned16(params[0].classId()),
                    new Cosem_Object_Instance_Id(params[0].obisCode().bytes()), new Integer8(params[0].methodId()));
            requestNormal.method_invocation_parameters.setValue(DataConverter.toPdu(params[0].methodParameter()));

            request.setaction_request_normal(requestNormal);
        }
        else {
            Action_Request_With_List requestList = new Action_Request_With_List();
            requestList.invoke_id_and_priority = id;
            requestList.cosem_method_descriptor_list = new Action_Request_With_List.SubSeqOf_cosem_method_descriptor_list();
            requestList.method_invocation_parameters = new Action_Request_With_List.SubSeqOf_method_invocation_parameters();
            for (MethodParameter param : params) {
                Cosem_Method_Descriptor desc = new Cosem_Method_Descriptor(new Unsigned16(param.classId()),
                        new Cosem_Object_Instance_Id(param.obisCode().bytes()), new Integer8(param.methodId()));
                requestList.cosem_method_descriptor_list.add(desc);
                requestList.method_invocation_parameters.add(DataConverter.toPdu(param.methodParameter()));
            }
            request.setaction_request_with_list(requestList);
        }

        if (pduSizeOf(request) < maxSendPduSize()) {
            pdu = new COSEMpdu();
            pdu.setaction_request(request);
            result.add(pdu);
        }
        else {
            // PDU is too large to send in one chunk to the meter
            // use of several Datablocks instead

            // if (params.length == 1) {
            // request.action_request_normal.method_invocation_parameters.encode(baos);
            // dataBuffer = ByteBuffer.wrap(baos.getArray());
            //
            // Action_Request_With_First_Pblock requestFirstBlock = new Action_Request_With_First_Pblock();
            // requestFirstBlock.invoke_id_and_priority = id;
            // requestFirstBlock.cosem_method_descriptor = request.action_request_normal.cosem_method_descriptor;
            //
            // baos.reset();
            // int length = requestFirstBlock.encode(baos);
            // byte[] firstDataChunk = new byte[maxSendPduSize() - 2 - length];
            // dataBuffer.get(firstDataChunk, 0, firstDataChunk.length);
            // requestFirstBlock.pblock = new DataBlock_SA(new AxdrBoolean(false), new Unsigned32(0),
            // new AxdrOctetString(firstDataChunk));
            //
            // request.setaction_request_with_first_pblock(requestFirstBlock);
            // pdu = new COSEMpdu();
            // pdu.setaction_request(request);
            // result.add(pdu);
            // }
            // else {
            // baos.reset();
            // request.action_request_with_list.method_invocation_parameters.encode(baos);
            // dataBuffer = ByteBuffer.wrap(baos.getArray());
            //
            // Action_Request_With_List_And_First_Pblock requestListFirstBlock = new
            // Action_Request_With_List_And_First_Pblock();
            // requestListFirstBlock.invoke_id_and_priority = id;
            // requestListFirstBlock.cosem_method_descriptor_list = new
            // Action_Request_With_List_And_First_Pblock.SubSeqOf_cosem_method_descriptor_list();
            //
            // for (Cosem_Method_Descriptor desc : request.action_request_with_list.cosem_method_descriptor_list
            // .list()) {
            // requestListFirstBlock.cosem_method_descriptor_list.add(desc);
            // }
            //
            // baos.reset();
            // int length = requestListFirstBlock.encode(baos);
            // byte[] firstDataChunk = new byte[maxSendPduSize() - 2 - length];
            // dataBuffer.get(firstDataChunk, 0, firstDataChunk.length);
            // requestListFirstBlock.pblock = new DataBlock_SA(new AxdrBoolean(false), new Unsigned32(0),
            // new AxdrOctetString(firstDataChunk));
            //
            // request.setaction_request_with_list_and_first_pblock(requestListFirstBlock);
            // }
            //
            // int blockNr = 1;
            // while (dataBuffer.hasRemaining()) {
            // int blockLength = Math.min(maxSendPduSize() - 8, dataBuffer.remaining());
            // byte[] dataBlock = new byte[blockLength];
            // dataBuffer.get(dataBlock, 0, dataBlock.length);
            //
            // Action_Request_With_Pblock requestBlock = new Action_Request_With_Pblock();
            // requestBlock.pBlock = new DataBlock_SA(new AxdrBoolean(dataBuffer.remaining() == 0),
            // new Unsigned32(blockNr), new AxdrOctetString(dataBlock));
            //
            // request.setaction_request_with_pblock(requestBlock);
            // pdu = new COSEMpdu();
            // pdu.setaction_request(request);
            // result.add(pdu);
            // }
        }

        return result;
    }

    @Override
    protected void validateReferencingMethod() throws IOException {
        if ((negotiatedFeatures().value[2] & 0x1F) == 0) {
            disconnect(true);
            throw new IOException("Wrong referencing method. Remote smart meter can't use LN referencing");
        }
    }

    @Override
    protected int buildContextId() {
        if (connectionSettings().dataTransmissionLevel() == DataTransmissionLevel.ENCRYPTED) {
            return 3;
        }
        else {
            return 1;
        }
    }

}
