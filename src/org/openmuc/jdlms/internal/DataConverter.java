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
package org.openmuc.jdlms.internal;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.openmuc.jdlms.AttributeAddress;
import org.openmuc.jdlms.EventNotification;
import org.openmuc.jdlms.HexConverter;
import org.openmuc.jdlms.ObisCode;
import org.openmuc.jdlms.datatypes.BitString;
import org.openmuc.jdlms.datatypes.CosemDate;
import org.openmuc.jdlms.datatypes.CosemDateFormat;
import org.openmuc.jdlms.datatypes.CosemDateTime;
import org.openmuc.jdlms.datatypes.CosemTime;
import org.openmuc.jdlms.datatypes.DataObject;
import org.openmuc.jdlms.internal.asn1.axdr.types.AxdrBitString;
import org.openmuc.jdlms.internal.asn1.axdr.types.AxdrBoolean;
import org.openmuc.jdlms.internal.asn1.axdr.types.AxdrNull;
import org.openmuc.jdlms.internal.asn1.axdr.types.AxdrOctetString;
import org.openmuc.jdlms.internal.asn1.axdr.types.AxdrVisibleString;
import org.openmuc.jdlms.internal.asn1.cosem.Data;
import org.openmuc.jdlms.internal.asn1.cosem.Data.Choices;
import org.openmuc.jdlms.internal.asn1.cosem.Data.SubSeqOf_array;
import org.openmuc.jdlms.internal.asn1.cosem.Data.SubSeqOf_structure;
import org.openmuc.jdlms.internal.asn1.cosem.EVENT_NOTIFICATION_Request;
import org.openmuc.jdlms.internal.asn1.cosem.Enum;
import org.openmuc.jdlms.internal.asn1.cosem.Integer16;
import org.openmuc.jdlms.internal.asn1.cosem.Integer32;
import org.openmuc.jdlms.internal.asn1.cosem.Integer64;
import org.openmuc.jdlms.internal.asn1.cosem.Integer8;
import org.openmuc.jdlms.internal.asn1.cosem.Unsigned16;
import org.openmuc.jdlms.internal.asn1.cosem.Unsigned32;
import org.openmuc.jdlms.internal.asn1.cosem.Unsigned64;
import org.openmuc.jdlms.internal.asn1.cosem.Unsigned8;

public final class DataConverter {

    public static DataObject toApi(Data pdu) {

        Choices choice = pdu.getChoiceIndex();
        ByteBuffer buf;
        List<DataObject> innerData;

        switch (choice) {
            case ARRAY:
                innerData = new LinkedList<DataObject>();
                for (Data item : pdu.array.list()) {
                    innerData.add(toApi(item));
                }
                return DataObject.newArrayData(innerData);

            case STRUCTURE:
                innerData = new LinkedList<DataObject>();
                for (Data item : pdu.structure.list()) {
                    innerData.add(toApi(item));
                }
                return DataObject.newStructureData(innerData);

            case BOOL:
                return DataObject.newBoolData(pdu.bool.getValue());

            case BIT_STRING:
                return DataObject.newBitStringData(new BitString(pdu.bit_string.getValue(), pdu.bit_string.getNumBits()));

            case DOUBLE_LONG:
                return DataObject.newInteger32Data((int) pdu.double_long.getValue());

            case DOUBLE_LONG_UNSIGNED:
                return DataObject.newUInteger32Data(pdu.double_long_unsigned.getValue());

            case OCTET_STRING:
                return DataObject.newOctetStringData(pdu.octet_string.getValue());

            case VISIBLE_STRING:
                return DataObject.newVisibleStringData(pdu.visible_string.getValue());

            case BCD:
                return DataObject.newBcdData((byte) pdu.bcd.getValue());

            case INTEGER:
                return DataObject.newInteger8Data((byte) pdu.integer.getValue());

            case LONG_INTEGER:
                return DataObject.newInteger16Data((short) pdu.long_integer.getValue());

            case UNSIGNED:
                return DataObject.newUInteger8Data((short) pdu.unsigned.getValue());

            case LONG_UNSIGNED:
                return DataObject.newUInteger16Data((int) pdu.long_unsigned.getValue());

            case LONG64:
                return DataObject.newInteger64Data(pdu.long64.getValue());
            case LONG64_UNSIGNED:
                return DataObject.newUInteger64Data(pdu.long64_unsigned.getValue());

            case ENUMERATE:
                return DataObject.newEnumerateData((int) pdu.enumerate.getValue());

            case FLOAT32:
                buf = ByteBuffer.wrap(pdu.float32.getValue());
                return DataObject.newFloat32Data(buf.getFloat());

            case FLOAT64:
                buf = ByteBuffer.wrap(pdu.float64.getValue());
                  return DataObject.newFloat64Data(buf.getDouble());

            case DATE_TIME:
                CosemDateTime dateTime = CosemDateTime.decode(pdu.date_time.getValue());
                return DataObject.newDateTimeData(dateTime);

            case DATE:
                CosemDate date = CosemDate.decode(pdu.date.getValue());
                return DataObject.newDateData(date);

            case TIME:
                CosemTime time = CosemTime.decode(pdu.time.getValue());
                return DataObject.newTimeData(time);

            case COMPACT_ARRAY:
                // TODO: support compact arrays
            case DONT_CARE:
            case NULL_DATA:
            default:
                return DataObject.newNullData();
        }

    }

    public static Data toPdu(DataObject data) {
        Data result = new Data();

        Choices choice = data.choiceIndex();

        if (choice == Choices.DONT_CARE || choice == Choices.NULL_DATA) {
            result.setdont_care(new AxdrNull());
        }
        else if (data.isCosemDateFormat()) {
            CosemDateFormat cal = data.value();
            result.setoctet_string(new AxdrOctetString(cal.encode()));

        }
        else if (data.isNumber()) {
            result = convertNumberToPduData(data, choice);

        }
        else if (data.isByteArray()) {
            result = converByteArrayToPduData(data, choice);

        }
        else if (data.isBitString()) {
            BitString value = data.value();
            result.setbit_string(new AxdrBitString(value.bitString(), value.numBits()));

        }
        else if (choice == Choices.BOOL) {
            Boolean boolValue = data.value();
            result.setbool(new AxdrBoolean(boolValue));

        }
        else if (data.isComplex()) {
            result = convertComplexToPduData(data, choice);
        }

        return result;
    }

    private static Data convertComplexToPduData(DataObject data, Choices choice) {
        List<DataObject> dataList = data.value();
        Data result = new Data();

        if (choice == Choices.STRUCTURE) {
            result.setstructure(new SubSeqOf_structure());
            for (DataObject element : dataList) {
                result.structure.add(toPdu(element));
            }
        }
        else if (choice == Choices.ARRAY) {
            result.setarray(new SubSeqOf_array());
            for (DataObject element : dataList) {
                result.array.add(toPdu(element));
            }
        }
        else if (choice == Choices.COMPACT_ARRAY) {
            // TODO Implement compact array
        }

        return result;
    }

    private static Data converByteArrayToPduData(DataObject data, Choices choice) {
        byte[] value = data.value();

        Data result = new Data();

        switch (choice) {
            case OCTET_STRING:
                result.setoctet_string(new AxdrOctetString(value));
                break;
            case VISIBLE_STRING:
                result.setvisible_string(new AxdrVisibleString(value));
                break;

            default:
                // can't be rached
                throw new IllegalArgumentException("No such array: " + choice);
        }

        return result;
    }

    private static Data convertNumberToPduData(DataObject data, Choices choice) {
        ByteBuffer buffer;
        Number value = data.value();

        Data result = new Data();

        switch (choice) {
            case FLOAT64:

                buffer = ByteBuffer.allocate(8);
                buffer.putDouble(value.doubleValue());
                buffer.flip();

                result.setfloat64(new AxdrOctetString(8, buffer.array()));
                break;

            case FLOAT32:
                buffer = ByteBuffer.allocate(4);
                buffer.putFloat((float) value.doubleValue()); // some reason they want to but a double for a float...
                buffer.flip();

                result.setfloat32(new AxdrOctetString(4, buffer.array()));
                break;

            case ENUMERATE:
                result.setenumerate(new Enum(value.longValue()));
                break;

            case LONG64_UNSIGNED:
                result.setlong64_unsigned(new Unsigned64(value.longValue()));
                break;

            case LONG64:
                result.setlong64(new Integer64(value.longValue()));
                break;

            case LONG_UNSIGNED:
                result.setlong_unsigned(new Unsigned16(value.longValue()));
                break;

            case UNSIGNED:
                result.setunsigned(new Unsigned8(value.longValue()));
                break;

            case LONG_INTEGER:
                result.setlong_integer(new Integer16(value.longValue()));
                break;
            case INTEGER:
                result.setinteger(new Integer8(value.longValue()));
                break;
            case BCD:
                result.setbcd(new Integer8(value.longValue()));
                break;
            case DOUBLE_LONG_UNSIGNED:
                result.setdouble_long_unsigned(new Unsigned32(value.longValue()));
                break;
            case DOUBLE_LONG:
                result.setdouble_long(new Integer32(value.longValue()));

                break;
            default:
                // can't be rached
                throw new IllegalArgumentException("No such number: " + choice);
        }
        return result;
    }

    public static EventNotification toApi(EVENT_NOTIFICATION_Request pdu) {
        int classId = (int) pdu.cosem_attribute_descriptor.class_id.getValue();
        int attributeId = (int) pdu.cosem_attribute_descriptor.attribute_id.getValue();

        byte[] obisCodeBytes = pdu.cosem_attribute_descriptor.instance_id.getValue();

        Long timestamp = null;
        if (pdu.time.isUsed()) {
            CosemDateTime dateTime = CosemDateTime.decode(pdu.time.getValue().getValue());

            timestamp = dateTime.toCalendar().getTimeInMillis();
        }

        DataObject newValue = null;
        if (pdu.attribute_value != null) {
            newValue = toApi(pdu.attribute_value);
        }

        return new EventNotification(new AttributeAddress(classId, new ObisCode(obisCodeBytes), attributeId), newValue,
                timestamp);
    }

    /**
     * Don't let anyone instantiate this class.
     */
    private DataConverter() {
    }
}
