/*
 * Copyright 2012 - 2013 Anton Tananaev (anton.tananaev@gmail.com)
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

import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.traccar.BaseProtocolDecoder;
import org.traccar.ServerManager;
import org.traccar.helper.ChannelBufferTools;
import org.traccar.helper.Log;
import org.traccar.model.ExtendedInfoFormatter;
import org.traccar.model.Position;

public class EnforaProtocolDecoder extends BaseProtocolDecoder {

    public EnforaProtocolDecoder(ServerManager serverManager) {
        super(serverManager);
    }

    private static final Pattern pattern = Pattern.compile(
            "GPRMC," +
            "(\\d{2})(\\d{2})(\\d{2}).(\\d{2})," + // Time (HHMMSS.SS)
            "([AV])," +                  // Validity
            "(\\d{2})(\\d{2}.\\d{6})," + // Latitude (DDMM.MMMMMM)
            "([NS])," +
            "(\\d{3})(\\d{2}.\\d{6})," + // Longitude (DDDMM.MMMMMM)
            "([EW])," +
            "(\\d+.\\d)?," +             // Speed
            "(\\d+.\\d)?," +             // Course
            "(\\d{2})(\\d{2})(\\d{2})," + // Date (DDMMYY)
            ".*[\r\n\u0000]*");

    public static final int IMEI_LENGTH = 15;

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {

        ChannelBuffer buf = (ChannelBuffer) msg;

        // Find IMEI (Modem ID)
        String imei = null;
        for (int first = -1, i = 0; i < buf.readableBytes(); i++) {
            if (!Character.isDigit((char) buf.getByte(i))) {
                first = i + 1;
            }

            // Found digit string
            if (i - first == IMEI_LENGTH - 1) {
                imei = buf.toString(first, IMEI_LENGTH, Charset.defaultCharset());
                break;
            }
        }

        // Write log
        if (imei == null) {
            Log.warning("Enfora decoder failed to find IMEI");
            return null;
        }

        // Find GPSMC string
        Integer start = ChannelBufferTools.find(buf, 0, buf.readableBytes(), "GPRMC");
        if (start == null) {
            // Message does not contain GPS data
            return null;
        }
        String sentence = buf.toString(start, buf.readableBytes() - start, Charset.defaultCharset());

        // Parse message
        Matcher parser = pattern.matcher(sentence);
        if (!parser.matches()) {
            return null;
        }

        // Create new position
        Position position = new Position();
        ExtendedInfoFormatter extendedInfo = new ExtendedInfoFormatter("enfora");
        Integer index = 1;

        // Get device by IMEI
        try {
            position.setDeviceId(getDataManager().getDeviceByImei(imei).getId());
        } catch(Exception error) {
            Log.warning("Unknown device - " + imei);
            return null;
        }

        // Time
        Calendar time = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        time.clear();
        time.set(Calendar.HOUR_OF_DAY, Integer.valueOf(parser.group(index++)));
        time.set(Calendar.MINUTE, Integer.valueOf(parser.group(index++)));
        time.set(Calendar.SECOND, Integer.valueOf(parser.group(index++)));
        time.set(Calendar.MILLISECOND, Integer.valueOf(parser.group(index++)) * 10);

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

        // Altitude
        position.setAltitude(0.0);

        // Speed
        position.setSpeed(Double.valueOf(parser.group(index++)));

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

        position.setExtendedInfo(extendedInfo.toString());
        return position;
    }

}
