/*
 * Copyright 2014 Anton Tananaev (anton.tananaev@gmail.com)
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

import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.traccar.BaseProtocolDecoder;
import org.traccar.ServerManager;
import org.traccar.helper.Log;
import org.traccar.model.ExtendedInfoFormatter;
import org.traccar.model.Position;

public class PiligrimProtocolDecoder extends BaseProtocolDecoder {
    
    public PiligrimProtocolDecoder(ServerManager serverManager) {
        super(serverManager);
    }

    private void sendResponse(Channel channel, String message) {
        if (channel != null) {
            HttpResponse response = new DefaultHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.setContent(ChannelBuffers.copiedBuffer(
                    ByteOrder.BIG_ENDIAN, message, Charset.defaultCharset()));
            channel.write(response);
        }
    }

    private static final int MSG_GPS = 0xF1;
    private static final int MSG_GPS_SENSORS = 0xF2;
    private static final int MSG_EVENTS = 0xF3;

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {
        
        HttpRequest request = (HttpRequest) msg;
        String uri = request.getUri();
        
        if (uri.startsWith("/config")) {

            sendResponse(channel, "CONFIG: OK");
        
        } else if (uri.startsWith("/addlog")) {

            sendResponse(channel, "ADDLOG: OK");
        
        } else if (uri.startsWith("/inform")) {

            sendResponse(channel, "INFORM: OK");
        
        } else if (uri.startsWith("/bingps")) {

            sendResponse(channel, "BINGPS: OK");
            
            // Identification
            long deviceId;
            QueryStringDecoder decoder = new QueryStringDecoder(request.getUri());
            String imei = decoder.getParameters().get("imei").get(0);
            try {
                deviceId = getDataManager().getDeviceByImei(imei).getId();
            } catch(Exception error) {
                Log.warning("Unknown device - " + imei);
                return null;
            }

            List<Position> positions = new LinkedList<Position>();
            ChannelBuffer buf = request.getContent();
            
            while (buf.readableBytes() > 2) {

                buf.readUnsignedByte(); // header
                int type = buf.readUnsignedByte();
                buf.readUnsignedByte(); // length
                
                if (type == MSG_GPS || type == MSG_GPS_SENSORS) {
                    
                    Position position = new Position();
                    ExtendedInfoFormatter extendedInfo = new ExtendedInfoFormatter("piligrim");
                    position.setDeviceId(deviceId);
                    
                    // Time
                    Calendar time = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    time.clear();
                    time.set(Calendar.DAY_OF_MONTH, buf.readUnsignedByte());
                    time.set(Calendar.MONTH, (buf.getByte(buf.readerIndex()) & 0x0f) - 1);
                    time.set(Calendar.YEAR, 2010 + (buf.readUnsignedByte() >> 4));
                    time.set(Calendar.HOUR_OF_DAY, buf.readUnsignedByte());
                    time.set(Calendar.MINUTE, buf.readUnsignedByte());
                    time.set(Calendar.SECOND, buf.readUnsignedByte());
                    position.setTime(time.getTime());
                    
                    // Latitude
                    double latitude = buf.readUnsignedByte();
                    latitude += buf.readUnsignedByte() / 60.0;
                    latitude += buf.readUnsignedByte() / 6000.0;
                    latitude += buf.readUnsignedByte() / 600000.0;
                    
                    // Longitude
                    double longitude = buf.readUnsignedByte();
                    longitude += buf.readUnsignedByte() / 60.0;
                    longitude += buf.readUnsignedByte() / 6000.0;
                    longitude += buf.readUnsignedByte() / 600000.0;
                    
                    // Hemisphere
                    int flags = buf.readUnsignedByte();
                    if ((flags & 0x01) != 0) latitude = -latitude;
                    if ((flags & 0x02) != 0) longitude = -longitude;
                    position.setLatitude(latitude);
                    position.setLongitude(longitude);
                    position.setAltitude(0.0);
                    
                    // Satellites
                    int satellites = buf.readUnsignedByte();
                    extendedInfo.set("satellites", satellites);
                    position.setValid(satellites >= 3);
                    
                    // Speed
                    position.setSpeed((double) buf.readUnsignedByte());
                    
                    // Course
                    double course = buf.readUnsignedByte() << 1;
                    course += (flags >> 2) & 1;
                    course += buf.readUnsignedByte() / 100.0;
                    position.setCourse(course);

                    // Sensors
                    if (type == MSG_GPS_SENSORS) {

                        // External power
                        double power = buf.readUnsignedByte();
                        power += buf.readUnsignedByte() << 8;
                        extendedInfo.set("power", power / 100);

                        // Battery
                        double battery = buf.readUnsignedByte();
                        battery += buf.readUnsignedByte() << 8;
                        extendedInfo.set("battery", battery / 100);
                        
                        buf.skipBytes(6);
                        
                    }
                    
                    position.setExtendedInfo(extendedInfo.toString());
                    positions.add(position);
                    
                } else if (type == MSG_EVENTS) {
                    
                    buf.skipBytes(13);
                    
                }
                
            }
            
            return positions;
        }

        return null;
    }

}
