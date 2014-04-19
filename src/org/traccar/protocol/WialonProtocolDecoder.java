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

import java.util.Calendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.traccar.BaseProtocolDecoder;
import org.traccar.ServerManager;
import org.traccar.helper.Log;
import org.traccar.model.ExtendedInfoFormatter;
import org.traccar.model.Position;

public class WialonProtocolDecoder extends BaseProtocolDecoder {

    private Long deviceId;

    public WialonProtocolDecoder(ServerManager serverManager) {
        super(serverManager);
    }

    private static final Pattern pattern = Pattern.compile(
            "#S?D#" +
            "(\\d{2})(\\d{2})(\\d{2});" +  // Date (DDMMYY)
            "(\\d{2})(\\d{2})(\\d{2});" +  // Time (HHMMSS)
            "(\\d{2})(\\d{2}\\.\\d+);" +   // Latitude (DDMM.MMMM)
            "([NS]);" +
            "(\\d{3})(\\d{2}\\.\\d+);" +   // Longitude (DDDMM.MMMM)
            "([EW]);" +
            "(\\d+\\.?\\d*);" +            // Speed
            "(\\d+\\.?\\d*);" +            // Course
            "(\\d+\\.?\\d*);" +            // Altitude
            "(\\d+)" +                     // Satellites
            ".*");                         // Full format

    private void sendResponse(Channel channel, String prefix, Integer number) {
        if (channel != null) {
            StringBuilder response = new StringBuilder(prefix);
            if (number != null) {
                response.append(number);
            }
            response.append("\r\n");
            channel.write(response.toString());
        }
    }

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {

        String sentence = (String) msg;

        // Detect device ID
        if (sentence.startsWith("#L#")) {
            String imei = sentence.substring(3, sentence.indexOf(';'));
            try {
                deviceId = getDataManager().getDeviceByImei(imei).getId();
                sendResponse(channel, "#AL#", 1);
            } catch(Exception error) {
                Log.warning("Unknown device - " + imei);
            }
        }

        // Heartbeat
        else if (sentence.startsWith("#P#")) {
            sendResponse(channel, "#AP#", null);
        }
        
        // Parse message
        else if ((sentence.startsWith("#SD#") || sentence.startsWith("#D#")) && deviceId != null) {

            // Parse message
            Matcher parser = pattern.matcher(sentence);
            if (!parser.matches()) {
                return null;
            }

            // Create new position
            Position position = new Position();
            ExtendedInfoFormatter extendedInfo = new ExtendedInfoFormatter("wialon");
            position.setDeviceId(deviceId);

            Integer index = 1;

            // Date and Time
            Calendar time = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            time.clear();
            time.set(Calendar.DAY_OF_MONTH, Integer.valueOf(parser.group(index++)));
            time.set(Calendar.MONTH, Integer.valueOf(parser.group(index++)) - 1);
            time.set(Calendar.YEAR, 2000 + Integer.valueOf(parser.group(index++)));
            time.set(Calendar.HOUR_OF_DAY, Integer.valueOf(parser.group(index++)));
            time.set(Calendar.MINUTE, Integer.valueOf(parser.group(index++)));
            time.set(Calendar.SECOND, Integer.valueOf(parser.group(index++)));
            position.setTime(time.getTime());

            // Latitude
            Double latitude = Double.valueOf(parser.group(index++));
            latitude += Double.valueOf(parser.group(index++)) / 60;
            if (parser.group(index++).compareTo("S") == 0) latitude = -latitude;
            position.setLatitude(latitude);

            // Longitude
            Double longitude = Double.valueOf(parser.group(index++));
            longitude += Double.valueOf(parser.group(index++)) / 60;
            if (parser.group(index++).compareTo("W") == 0) longitude = -longitude;
            position.setLongitude(longitude);

            // Speed
            position.setSpeed(Double.valueOf(parser.group(index++)));

            // Course
            position.setCourse(Double.valueOf(parser.group(index++)));

            // Altitude
            position.setAltitude(Double.valueOf(parser.group(index++)));
            
            // Satellites
            int satellites = Integer.valueOf(parser.group(index++));
            position.setValid(satellites >= 3);
            extendedInfo.set("satellites", satellites);
            
            // Extended info
            position.setExtendedInfo(extendedInfo.toString());

            // Send response
            sendResponse(channel, "#AD#", 1);

            return position;
        }

        return null;
    }

}
