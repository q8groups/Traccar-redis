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
import java.util.LinkedList;
import java.util.List;
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

public class Tlt2hProtocolDecoder extends BaseProtocolDecoder {

    public Tlt2hProtocolDecoder(ServerManager serverManager) {
        super(serverManager);
    }

    static private Pattern patternHeader = Pattern.compile(
            "#(\\d+)#" +                   // IMEI
            "[^#]+#" +
            "\\d+#" +
            "([^#]+)#" +                   // Status
            "\\d+");                       // Number of records

    static private Pattern patternPosition = Pattern.compile(
            "#([0-9a-f]+)?" +              // Cell info
            "\\$GPRMC," +
            "(\\d{2})(\\d{2})(\\d{2})\\.(\\d+)," + // Time (HHMMSS.SSS)
            "([AV])," +                    // Validity
            "(\\d+)(\\d{2}\\.\\d+)," +     // Latitude (DDMM.MMMM)
            "([NS])," +
            "(\\d+)(\\d{2}\\.\\d+)," +     // Longitude (DDDMM.MMMM)
            "([EW])," +
            "(\\d+\\.\\d{2})?," +          // Speed
            "(\\d+\\.\\d{2})?," +          // Course
            "(\\d{2})(\\d{2})(\\d{2})" +   // Date (DDMMYY)
            ".+");                         // Other (Checksumm)

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {

        String sentence = (String) msg;

        // Decode header
        String header = sentence.substring(0, sentence.indexOf('\r'));
        Matcher parser = patternHeader.matcher(header);
        if (!parser.matches()) {
            return null;
        }

        // Get device identifier
        String imei = parser.group(1);
        long deviceId;
        try {
            deviceId = getDataManager().getDeviceByImei(imei).getId();
        } catch(Exception error) {
            Log.warning("Unknown device - " + imei);
            return null;
        }
        
        // Get status
        String status = parser.group(2);
        
        String[] messages = sentence.substring(sentence.indexOf('\n') + 1).split("\r\n");
        List<Position> positions = new LinkedList<Position>();
        
        for (String message : messages) {
            parser = patternPosition.matcher(message);
            if (parser.matches()) {
                Position position = new Position();
                ExtendedInfoFormatter extendedInfo = new ExtendedInfoFormatter("tlt2h");
                position.setDeviceId(deviceId);

                Integer index = 1;
                
                // Cell
                extendedInfo.set("cell", parser.group(index++));

                // Time
                Calendar time = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                time.clear();
                time.set(Calendar.HOUR_OF_DAY, Integer.valueOf(parser.group(index++)));
                time.set(Calendar.MINUTE, Integer.valueOf(parser.group(index++)));
                time.set(Calendar.SECOND, Integer.valueOf(parser.group(index++)));
                index += 1; // Skip milliseconds

                // Validity
                position.setValid(parser.group(index++).compareTo("A") == 0 ? true : false);

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
                String speed = parser.group(index++);
                if (speed != null) {
                    position.setSpeed(Double.valueOf(speed));
                } else {
                    position.setSpeed(0.0);
                }

                // Course
                String course = parser.group(index++);
                if (course != null) {
                    position.setCourse(Double.valueOf(course));
                } else {
                    position.setCourse(0.0);
                }

                // Date
                time.set(Calendar.DAY_OF_MONTH, Integer.valueOf(parser.group(index++)));
                time.set(Calendar.MONTH, Integer.valueOf(parser.group(index++)) - 1);
                time.set(Calendar.YEAR, 2000 + Integer.valueOf(parser.group(index++)));
                position.setTime(time.getTime());

                // Altitude
                position.setAltitude(0.0);
                
                // Status
                extendedInfo.set("status", status);
                
                position.setExtendedInfo(extendedInfo.toString());
                positions.add(position);
            }
        }

        return positions;
    }

}
