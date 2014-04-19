/*
 * Copyright 2012 - 2014 Anton Tananaev (anton.tananaev@gmail.com)
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

public class T55ProtocolDecoder extends BaseProtocolDecoder {

    private Long deviceId;

    public T55ProtocolDecoder(ServerManager serverManager) {
        super(serverManager);
    }

    private static final Pattern patternGPRMC = Pattern.compile(
            "\\$GPRMC," +
            "(\\d{2})(\\d{2})(\\d{2})\\.?\\d*," + // Time (HHMMSS.SSS)
            "([AV])," +                    // Validity
            "(\\d{2})(\\d{2}\\.\\d+)," +   // Latitude (DDMM.MMMM)
            "([NS])," +
            "(\\d{3})(\\d{2}\\.\\d+)," +   // Longitude (DDDMM.MMMM)
            "([EW])," +
            "(\\d+\\.?\\d*)?," +           // Speed
            "(\\d+\\.?\\d*)?," +           // Course
            "(\\d{2})(\\d{2})(\\d{2})" +   // Date (DDMMYY)
            ".+");

    private static final Pattern patternGPGGA = Pattern.compile(
            "\\$GPGGA," +
            "(\\d{2})(\\d{2})(\\d{2})\\.?\\d*," + // Time
            "(\\d{2})(\\d{2}\\.\\d+)," +   // Latitude
            "([NS])," +
            "(\\d{3})(\\d{2}\\.\\d+)," +   // Longitude
            "([EW])," +
            ".+");

    private static final Pattern patternGPRMA = Pattern.compile(
            "\\$GPRMA," +
            "([AV])," +                    // Validity
            "(\\d{2})(\\d{2}\\.\\d+)," +   // Latitude
            "([NS])," +
            "(\\d{3})(\\d{2}\\.\\d+)," +   // Longitude
            "([EW]),,," +
            "(\\d+\\.?\\d*)?," +           // Speed
            "(\\d+\\.?\\d*)?," +           // Course
            ".+");
    
    private static final Pattern patternTRCCR = Pattern.compile(
            "\\$TRCCR," +
            "(\\d{4})(\\d{2})(\\d{2})" +   // Date (YYYYMMDD)
            "(\\d{2})(\\d{2})(\\d{2})\\.?\\d*," + // Time (HHMMSS.SSS)
            "([AV])," +                    // Validity
            "(-?\\d+\\.\\d+)," +           // Latitude
            "(-?\\d+\\.\\d+)," +           // Longitude
            "(\\d+\\.\\d+)," +             // Speed
            "(\\d+\\.\\d+)," +             // Course
            "(-?\\d+\\.\\d+)," +           // Altitude
            "(\\d+\\.?\\d*)," +            // Battery
            ".+");
    
    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {

        String sentence = (String) msg;

        // Identification
        if (sentence.startsWith("$PGID")) {
            String imei = sentence.substring(6, sentence.length() - 3);
            try {
                deviceId = getDataManager().getDeviceByImei(imei).getId();
            } catch(Exception error) {
                Log.warning("Unknown device - " + imei);
            }
        }

        // Identification
        else if (sentence.startsWith("$PCPTI")) {
            String id = sentence.substring(7, sentence.indexOf(",", 7));
            try {
                deviceId = getDataManager().getDeviceByImei(id).getId();
            } catch(Exception error) {
                Log.warning("Unknown device - " + id);
            }
        }

        // Identification
        else if (sentence.startsWith("IMEI")) {
            String id = sentence.substring(5, sentence.length());
            try {
                deviceId = getDataManager().getDeviceByImei(id).getId();
            } catch(Exception error) {
                Log.warning("Unknown device - " + id);
            }
        }
        
        // Identification
        else if (Character.isDigit(sentence.charAt(0)) & sentence.length() == 15) {
            try {
                deviceId = getDataManager().getDeviceByImei(sentence).getId();
            } catch(Exception error) {
                Log.warning("Unknown device - " + sentence);
            }
        }

        // Location
        else if (sentence.startsWith("$GPRMC") && deviceId != null) {

            // Send response
            if (channel != null) {
                channel.write("OK1\r\n");
            }

            // Parse message
            Matcher parser = patternGPRMC.matcher(sentence);
            if (!parser.matches()) {
                return null;
            }

            // Create new position
            Position position = new Position();
            ExtendedInfoFormatter extendedInfo = new ExtendedInfoFormatter("t55");
            position.setDeviceId(deviceId);

            Integer index = 1;

            // Time
            Calendar time = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            time.clear();
            time.set(Calendar.HOUR_OF_DAY, Integer.valueOf(parser.group(index++)));
            time.set(Calendar.MINUTE, Integer.valueOf(parser.group(index++)));
            time.set(Calendar.SECOND, Integer.valueOf(parser.group(index++)));

            // Validity
            position.setValid(parser.group(index++).compareTo("A") == 0);

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

            position.setExtendedInfo(extendedInfo.toString());
            return position;
        }

        // Location
        else if (sentence.startsWith("$GPGGA") && deviceId != null) {

            // Parse message
            Matcher parser = patternGPGGA.matcher(sentence);
            if (!parser.matches()) {
                return null;
            }

            // Create new position
            Position position = new Position();
            ExtendedInfoFormatter extendedInfo = new ExtendedInfoFormatter("t55");
            position.setDeviceId(deviceId);

            Integer index = 1;

            // Time
            Calendar time = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            time.set(Calendar.HOUR_OF_DAY, Integer.valueOf(parser.group(index++)));
            time.set(Calendar.MINUTE, Integer.valueOf(parser.group(index++)));
            time.set(Calendar.SECOND, Integer.valueOf(parser.group(index++)));
            time.set(Calendar.MILLISECOND, 0);
            position.setTime(time.getTime());

            // Validity
            position.setValid(true);

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
            position.setSpeed(0.0);
            
            // Course
            position.setCourse(0.0);

            // Altitude
            position.setAltitude(0.0);

            position.setExtendedInfo(extendedInfo.toString());
            return position;
        }

        // Location
        else if (sentence.startsWith("$GPRMA") && deviceId != null) {

            // Parse message
            Matcher parser = patternGPRMA.matcher(sentence);
            if (!parser.matches()) {
                return null;
            }

            // Create new position
            Position position = new Position();
            ExtendedInfoFormatter extendedInfo = new ExtendedInfoFormatter("t55");
            position.setDeviceId(deviceId);

            Integer index = 1;

            // Time
            position.setTime(Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTime());

            // Validity
            position.setValid(parser.group(index++).compareTo("A") == 0);

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

            // Altitude
            position.setAltitude(0.0);

            position.setExtendedInfo(extendedInfo.toString());
            return position;
        }

        // Location
        else if (sentence.startsWith("$TRCCR") && deviceId != null) {

            // Parse message
            Matcher parser = patternTRCCR.matcher(sentence);
            if (!parser.matches()) {
                return null;
            }

            // Create new position
            Position position = new Position();
            ExtendedInfoFormatter extendedInfo = new ExtendedInfoFormatter("t55");
            position.setDeviceId(deviceId);

            Integer index = 1;

            // Time
            Calendar time = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            time.clear();
            time.set(Calendar.YEAR, Integer.valueOf(parser.group(index++)));
            time.set(Calendar.MONTH, Integer.valueOf(parser.group(index++)) - 1);
            time.set(Calendar.DAY_OF_MONTH, Integer.valueOf(parser.group(index++)));
            time.set(Calendar.HOUR_OF_DAY, Integer.valueOf(parser.group(index++)));
            time.set(Calendar.MINUTE, Integer.valueOf(parser.group(index++)));
            time.set(Calendar.SECOND, Integer.valueOf(parser.group(index++)));
            position.setTime(time.getTime());

            // Validity
            position.setValid(parser.group(index++).compareTo("A") == 0);

            // Location
            position.setLatitude(Double.valueOf(parser.group(index++)));
            position.setLongitude(Double.valueOf(parser.group(index++)));
            position.setSpeed(Double.valueOf(parser.group(index++)));
            position.setCourse(Double.valueOf(parser.group(index++)));
            position.setAltitude(Double.valueOf(parser.group(index++)));

            // Battery
            extendedInfo.set("battery", parser.group(index++));

            position.setExtendedInfo(extendedInfo.toString());
            return position;
        }

        return null;
    }

}
