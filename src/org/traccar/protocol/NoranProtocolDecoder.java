/*
 * Copyright 2013 - 2014 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol;

import java.net.SocketAddress;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.TimeZone;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.traccar.BaseProtocolDecoder;
import org.traccar.ServerManager;
import org.traccar.helper.Log;
import org.traccar.model.ExtendedInfoFormatter;
import org.traccar.model.Position;

public class NoranProtocolDecoder extends BaseProtocolDecoder {

    public NoranProtocolDecoder(ServerManager serverManager) {
        super(serverManager);
    }

    private static final int MSG_UPLOAD_POSITION = 0x0008;
    private static final int MSG_CONTROL_RESPONSE = 0x8009;
    private static final int MSG_ALARM = 0x0003;
    private static final int MSG_SHAKE_HAND = 0x0000;
    private static final int MSG_SHAKE_HAND_RESPONSE = 0x8000;
    private static final int MSG_IMAGE_SIZE = 0x0200;
    private static final int MSG_IMAGE_PACKET = 0x0201;

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, SocketAddress remoteAddress, Object msg)
            throws Exception {
        
        ChannelBuffer buf = (ChannelBuffer) msg;
        
        buf.readUnsignedShort(); // length
        int type = buf.readUnsignedShort();
        
        if (type == MSG_SHAKE_HAND && channel != null) {
            
            ChannelBuffer response = ChannelBuffers.dynamicBuffer(ByteOrder.LITTLE_ENDIAN, 13);
            response.writeBytes(ChannelBuffers.copiedBuffer(ByteOrder.LITTLE_ENDIAN, "\r\n*KW", Charset.defaultCharset()));
            response.writeByte(0);
            response.writeShort(response.capacity());
            response.writeShort(MSG_SHAKE_HAND_RESPONSE);
            response.writeByte(1); // status
            response.writeBytes(ChannelBuffers.copiedBuffer(ByteOrder.LITTLE_ENDIAN, "\r\n", Charset.defaultCharset()));
            
            channel.write(response, remoteAddress);
        }
        
        else if (type == MSG_UPLOAD_POSITION ||
                 type == MSG_CONTROL_RESPONSE ||
                 type == MSG_ALARM) {
            
            // Create new position
            Position position = new Position();
            ExtendedInfoFormatter extendedInfo = new ExtendedInfoFormatter("noran");
            
            if (type == MSG_CONTROL_RESPONSE) {
                buf.readUnsignedInt(); // GIS ip
                buf.readUnsignedInt(); // GIS port
            }

            // Flags
            int flags = buf.readUnsignedByte();
            position.setValid((flags & 0x01) != 0);

            // Alarm type
            extendedInfo.set("alarm", buf.readUnsignedByte());

            // Location
            position.setSpeed((double) buf.readUnsignedByte());
            position.setCourse((double) buf.readUnsignedShort());
            position.setLongitude((double) buf.readFloat());
            position.setLatitude((double) buf.readFloat());
            position.setAltitude(0.0);

            // Time
            long timeValue = buf.readUnsignedInt();
            Calendar time = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            time.clear();
            time.set(Calendar.YEAR, 2000 + (int) (timeValue >> 26));
            time.set(Calendar.MONTH, (int) (timeValue >> 22 & 0x0f) - 1);
            time.set(Calendar.DAY_OF_MONTH, (int) (timeValue >> 17 & 0x1f));
            time.set(Calendar.HOUR_OF_DAY, (int) (timeValue >> 12 & 0x1f));
            time.set(Calendar.MINUTE, (int) (timeValue >> 6 & 0x3f));
            time.set(Calendar.SECOND, (int) (timeValue & 0x3f));
            position.setTime(time.getTime());

            // Identification
            ChannelBuffer rawId = buf.readBytes(11);
            int index = 0;
            while (rawId.readable() && rawId.readByte() != 0) {
                index += 1;
            }
            String id = rawId.toString(0, index, Charset.defaultCharset());
            try {
                position.setDeviceId(getDataManager().getDeviceByImei(id).getId());
            } catch(Exception error) {
                Log.warning("Unknown device - " + id);
                return null;
            }
            
            // IO status
            extendedInfo.set("io", buf.readUnsignedByte());
            
            // Fuel
            extendedInfo.set("fuel", buf.readUnsignedByte());
            
            position.setExtendedInfo(extendedInfo.toString());
            return position;
        }

        return null;
    }

}
