/*
 * Copyright 2013 Anton Tananaev (anton.tananaev@gmail.com)
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

import java.util.Calendar;
import java.util.TimeZone;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.traccar.BaseProtocolDecoder;
import org.traccar.ServerManager;
import org.traccar.helper.ChannelBufferTools;
import org.traccar.helper.Log;
import org.traccar.model.ExtendedInfoFormatter;
import org.traccar.model.Position;

public class GatorProtocolDecoder extends BaseProtocolDecoder {

    public GatorProtocolDecoder(ServerManager serverManager) {
        super(serverManager);
    }

    private static final int PACKET_HEARTBEAT = 0x21;
    private static final int PACKET_POSITION_DATA = 0x80;
    private static final int PACKET_ROLLCALL_RESPONSE = 0x81;
    private static final int PACKET_ALARM_DATA = 0x82;
    private static final int PACKET_TERMINAL_STATUS = 0x83;
    private static final int PACKET_MESSAGE = 0x84;
    private static final int PACKET_TERMINAL_ANSWER = 0x85;
    private static final int PACKET_BLIND_AREA = 0x8E;
    private static final int PACKET_PICTURE_FRAME = 0x54;
    private static final int PACKET_CAMERA_RESPONSE = 0x56;
    private static final int PACKET_PICTURE_DATA = 0x57;
    
    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {
        
        ChannelBuffer buf = (ChannelBuffer) msg;

        buf.skipBytes(2); // header
        int type = buf.readUnsignedByte();
        buf.readUnsignedShort(); // length

        // Pseudo IP address
        String id = String.valueOf(buf.readUnsignedInt());
        
        if (type == PACKET_POSITION_DATA ||
            type == PACKET_ROLLCALL_RESPONSE ||
            type == PACKET_ALARM_DATA ||
            type == PACKET_BLIND_AREA) {
            
            // Create new position
            Position position = new Position();
            ExtendedInfoFormatter extendedInfo = new ExtendedInfoFormatter("gator");

            // Identification
            try {
                position.setDeviceId(getDataManager().getDeviceByImei(id).getId());
            } catch(Exception error) {
                Log.warning("Unknown device - " + id);
            }
            
            // Date and time
            Calendar time = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            time.clear();
            time.set(Calendar.YEAR, 2000 + ChannelBufferTools.readHexInteger(buf, 2));
            time.set(Calendar.MONTH, ChannelBufferTools.readHexInteger(buf, 2) - 1);
            time.set(Calendar.DAY_OF_MONTH, ChannelBufferTools.readHexInteger(buf, 2));
            time.set(Calendar.HOUR_OF_DAY, ChannelBufferTools.readHexInteger(buf, 2));
            time.set(Calendar.MINUTE, ChannelBufferTools.readHexInteger(buf, 2));
            time.set(Calendar.SECOND, ChannelBufferTools.readHexInteger(buf, 2));
            position.setTime(time.getTime());

            // Location
            position.setLatitude(ChannelBufferTools.readCoordinate(buf));
            position.setLongitude(ChannelBufferTools.readCoordinate(buf));
            position.setSpeed(ChannelBufferTools.readHexInteger(buf, 4) * 0.539957);
            position.setCourse((double) ChannelBufferTools.readHexInteger(buf, 4));
            position.setAltitude(0.0);

            // Flags
            int flags = buf.readUnsignedByte();
            position.setValid((flags & 0x80) != 0);
            extendedInfo.set("satellites", flags & 0x0f);

            // Status
            extendedInfo.set("status", buf.readUnsignedByte());

            // Key switch
            extendedInfo.set("key", buf.readUnsignedByte());

            // Oil
            extendedInfo.set("oil", buf.readUnsignedShort() / 10.0);

            // Power
            extendedInfo.set("power", buf.readUnsignedByte() + buf.readUnsignedByte() / 100.0);

            // Milage
            extendedInfo.set("milage", buf.readUnsignedInt());

            position.setExtendedInfo(extendedInfo.toString());
            return position;
        }

        return null;
    }

}
