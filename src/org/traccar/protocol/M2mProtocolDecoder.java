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
import org.traccar.helper.Log;
import org.traccar.model.ExtendedInfoFormatter;
import org.traccar.model.Position;

public class M2mProtocolDecoder extends BaseProtocolDecoder {

    public M2mProtocolDecoder(ServerManager serverManager) {
        super(serverManager);
    }
    
    private boolean firstPacket = true;
    private Long deviceId;

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {

        ChannelBuffer buf = (ChannelBuffer) msg;

        // Remove offset
        for (int i = 0; i < buf.readableBytes(); i++) {
            int b = buf.getByte(i);
            if (b != 0x0b) {
                buf.setByte(i, b - 0x20);
            }
        }

        if (firstPacket) {
            
            firstPacket = false;

            // Read IMEI
            StringBuilder imei = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                int b = buf.readByte();
                if (i != 0) {
                    imei.append(b / 10);
                }
                imei.append(b % 10);
            }

            // Identification
            try {
                deviceId = getDataManager().getDeviceByImei(imei.toString()).getId();
            } catch(Exception error) {
                Log.warning("Unknown device - " + imei);
            }
            
        } else if (deviceId != null) {
            
            // Create new position
            Position position = new Position();
            ExtendedInfoFormatter extendedInfo = new ExtendedInfoFormatter("m2m");
            position.setDeviceId(deviceId);

            // Date and time
            Calendar time = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            time.clear();
            time.set(Calendar.DAY_OF_MONTH, buf.readUnsignedByte() & 0x3f);
            time.set(Calendar.MONTH, (buf.readUnsignedByte() & 0x3f) - 1);
            time.set(Calendar.YEAR, 2000 + buf.readUnsignedByte());
            time.set(Calendar.HOUR_OF_DAY, buf.readUnsignedByte() & 0x3f);
            time.set(Calendar.MINUTE, buf.readUnsignedByte() & 0x7f);
            time.set(Calendar.SECOND, buf.readUnsignedByte() & 0x7f);
            position.setTime(time.getTime());
            
            // Location
            int degrees = buf.readUnsignedByte();
            double latitude = buf.readUnsignedByte();
            latitude += buf.readUnsignedByte() / 100.0;
            latitude += buf.readUnsignedByte() / 10000.0;
            latitude /= 60;
            latitude += degrees;

            int b = buf.readUnsignedByte();

            degrees = (b & 0x7f) * 100 + buf.readUnsignedByte();
            double longitude = buf.readUnsignedByte();
            longitude += buf.readUnsignedByte() / 100.0;
            longitude += buf.readUnsignedByte() / 10000.0;
            longitude /= 60;
            longitude += degrees;
            
            if ((b & 0x80) != 0) {
                longitude = -longitude;
            }
            if ((b & 0x40) != 0) {
                latitude = -latitude;
            }
            
            position.setLatitude(latitude);
            position.setLongitude(longitude);
            position.setSpeed((double) buf.readUnsignedByte());
            position.setCourse(0.0);
            position.setAltitude(0.0);

            // Satellites
            int satellites = buf.readUnsignedByte();
            if (satellites == 0) {
                return null; // cell information
            }
            extendedInfo.set("satellites", satellites);
            position.setValid(true);

            // TODO decode everything else

            position.setExtendedInfo(extendedInfo.toString());
            return position;

        }

        return null;
    }

}
