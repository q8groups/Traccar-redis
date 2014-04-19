/*
 * Copyright 2012 Anton Tananaev (anton.tananaev@gmail.com)
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
package org.traccar;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.timeout.IdleStateAwareChannelHandler;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.traccar.helper.Log;
import org.traccar.model.Company;
import org.traccar.model.DataManager;
import org.traccar.model.Position;
import org.traccar.model.RealTimePosition;
import redis.clients.jedis.Jedis;

/**
 * Tracker message handler
 */
@ChannelHandler.Sharable
public class TrackerEventHandler extends IdleStateAwareChannelHandler {

    /**
     * Data manager
     */
    private DataManager dataManager;
    private Jedis jedis;
    private Company company;

    TrackerEventHandler(DataManager newDataManager) {
        super();
        dataManager = newDataManager;
    }

    
    
    
    private void processSinglePosition(Position position) {
        if (position == null) {
            Log.info("processSinglePosition null message");
        } else {
            
            StringBuilder s = new StringBuilder();
            s.append("device: ").append(position.getDeviceId()).append(", ");
            s.append("time: ").append(position.getTime()).append(", ");
            s.append("lat: ").append(position.getLatitude()).append(", ");
            s.append("lon: ").append(position.getLongitude());
            Log.info(s.toString());
        }
        
        

        // Write position to database
        try {
            if(jedis == null)
            {
                jedis = new Jedis("localhost");
            }
           
            char quot = '"';
            Timestamp ts = new Timestamp(position.getTime().getTime());
             String company_name = dataManager.getCompanyNameByDevice(position.getDeviceId());
            company_name = company_name.replace(" ", "-").toLowerCase();     
             StringBuilder s = new StringBuilder();
            s.append("{").append(quot).append("device").append(quot).append(":").append(quot).append(position.getDeviceId()).append(quot).append(", ");
            s.append(quot).append("time").append(quot).append(":").append(quot).append(ts.getTime()).append(quot).append(", ");
            s.append(quot).append("lat").append(quot).append(":").append(quot).append(position.getLatitude()).append(quot).append(", ");
            s.append(quot).append("lon").append(quot).append(":").append(quot).append(position.getLongitude()).append(quot).append(", ");
            s.append(quot).append("company").append(quot).append(":").append(quot).append(company_name).append(quot).append("}");
            Log.info(s.toString());
            jedis.publish("tracking_"+company_name, s.toString());
            
            Long id = dataManager.addPosition(position);
            if (id != null) {
                dataManager.updateLatestPosition(position.getDeviceId(), id);
                
            
            
                
            }
        } catch (Exception error) {
            Log.warning(error);
        }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
        if (e.getMessage() instanceof Position) {
            processSinglePosition((Position) e.getMessage());
        } else if (e.getMessage() instanceof List) {
            List<Position> positions = (List<Position>) e.getMessage();
            for (Position position : positions) {
                processSinglePosition(position);
            }
        }
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) {

        Log.info("Closing connection by disconnect");
        e.getChannel().close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        Log.info("Closing connection by exception");
        e.getChannel().close();
    }

    @Override
    public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e) {
      
        Log.info("Closing connection by timeout");
        e.getChannel().close();
    }
    
//  public void insertUser(RealTimePosition position){
// Map<String, String> positionProperties = new HashMap<String, String>();
//  positionProperties.put("device ", position.getDevice());
//  positionProperties.put("company ", position.getCompany());
//  positionProperties.put("time ", position.getTimeStamp());
//  positionProperties.put("lat ", position.getLat());
//  positionProperties.put("lon ", position.getLon());
// 
//  jedis.publish("tracking_"+position.getCompany().replace(' ', '-'), positionProperties);
//  jedis.hmset("user :" + user.getUsername(), userProperties);
// }

}
