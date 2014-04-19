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
package org.traccar;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.FixedLengthFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LineBasedFrameDecoder;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.traccar.geocode.GoogleReverseGeocoder;
import org.traccar.geocode.NominatimReverseGeocoder;
import org.traccar.geocode.ReverseGeocoder;
import org.traccar.helper.Log;
import org.traccar.http.WebServer;
import org.traccar.model.DataManager;
import org.traccar.model.DatabaseDataManager;
import org.traccar.protocol.*;

/**
 * Server Manager
 */
public class ServerManager {

    private final List<TrackerServer> serverList = new LinkedList<TrackerServer>();

    public void addTrackerServer(TrackerServer trackerServer) {
        serverList.add(trackerServer);
    }

    private boolean loggerEnabled;

    public boolean isLoggerEnabled() {
        return loggerEnabled;
    }

    private DataManager dataManager;

    public DataManager getDataManager() {
        return dataManager;
    }

    private ReverseGeocoder reverseGeocoder;

    public ReverseGeocoder getReverseGeocoder() {
        return reverseGeocoder;
    }

    private WebServer webServer;

    public WebServer getWebServer() {
        return webServer;
    }

    private Properties properties;

    public Properties getProperties() {
        return  properties;
    }

    public void init(String[] arguments) throws Exception {

        // Load properties
        properties = new Properties();
        if (arguments.length > 0) {
            properties.loadFromXML(new FileInputStream(arguments[0]));
        }

        // Init logger
        loggerEnabled = Boolean.valueOf(properties.getProperty("logger.enable"));
        if (loggerEnabled) {
            Log.setupLogger(properties);
        }

        dataManager = new DatabaseDataManager(properties);

        initGeocoder(properties);

        initXexunServer("xexun");
        initGps103Server("gps103");
        initTk103Server("tk103");
        initGl100Server("gl100");
        initGl200Server("gl200");
        initT55Server("t55");
        initXexun2Server("xexun2");
        initTotemServer("totem");
        initEnforaServer("enfora");
        initMeiligaoServer("meiligao");
        initMaxonServer("maxon");
        initSuntechServer("suntech");
        initProgressServer("progress");
        initH02Server("h02");
        initJt600Server("jt600");
        initEv603Server("ev603");
        initV680Server("v680");
        initPt502Server("pt502");
        initTr20Server("tr20");
        initNavisServer("navis");
        initMeitrackServer("meitrack");
        initSkypatrolServer("skypatrol");
        initGt02Server("gt02");
        initGt06Server("gt06");
        initMegastekServer("megastek");
        initNavigilServer("navigil");
        initGpsGateServer("gpsgate");
        initTeltonikaServer("teltonika");
        initMta6Server("mta6");
        initMta6CanServer("mta6can");
        initTlt2hServer("tlt2h");
        initSyrusServer("syrus");
        initWondexServer("wondex");
        initCellocatorServer("cellocator");
        initGalileoServer("galileo");
        initYwtServer("ywt");
        initTk102Server("tk102");
        initIntellitracServer("intellitrac");
        initXt7Server("xt7");
        initWialonServer("wialon");
        initCarscopServer("carscop");
        initApelServer("apel");
        initManPowerServer("manpower");
        initGlobalSatServer("globalsat");
        initAtrackServer("atrack");
        initPt3000Server("pt3000");
        initRuptelaServer("ruptela");
        initTopflytechServer("topflytech");
        initLaipacServer("laipac");
        initAplicomServer("aplicom");
        initGotopServer("gotop");
        initSanavServer("sanav");
        initGatorServer("gator");
        initNoranServer("noran");
        initM2mServer("m2m");
        initOsmAndServer("osmand");
        initEasyTrackServer("easytrack");
        initTaipServer("taip");
        initKhdServer("khd");
        initPiligrimServer("piligrim");
        initStl060Server("stl060");
        
        // Initialize web server
        if (Boolean.valueOf(properties.getProperty("http.enable"))) {
            webServer = new WebServer(properties);
        }
    }

    public void start() {
        if (webServer != null) {
            webServer.start();
        }
        for (Object server: serverList) {
            ((TrackerServer) server).start();
        }
    }

    public void stop() {
        for (Object server: serverList) {
            ((TrackerServer) server).stop();
        }

        // Release resources
        GlobalChannelFactory.release();
        GlobalTimer.release();

        if (webServer != null) {
            webServer.stop();
        }
    }

    public void destroy() {
        serverList.clear();
    }

    private void initGeocoder(Properties properties) throws IOException {
        if (Boolean.parseBoolean(properties.getProperty("geocoder.enable"))) {
            String type = properties.getProperty("geocoder.type");
            if (type != null && type.equals("nominatim")) {
                reverseGeocoder = new NominatimReverseGeocoder(
                        getProperties().getProperty("geocoder.url"));
            } else {
                reverseGeocoder = new GoogleReverseGeocoder();
            }
        }
    }

    private boolean isProtocolEnabled(Properties properties, String protocol) {
        String enabled = properties.getProperty(protocol + ".enable");
        if (enabled != null) {
            return Boolean.valueOf(enabled);
        }
        return false;
    }

    private void initXexunServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new XexunFrameDecoder());
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new XexunProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initGps103Server(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter1[] = { (byte) '\r', (byte) '\n' };
                    byte delimiter2[] = { (byte) '\n' };
                    byte delimiter3[] = { (byte) ';' };
                    pipeline.addLast("frameDecoder", new DelimiterBasedFrameDecoder(1024,
                            ChannelBuffers.wrappedBuffer(delimiter1),
                            ChannelBuffers.wrappedBuffer(delimiter2),
                            ChannelBuffers.wrappedBuffer(delimiter3)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new Gps103ProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initTk103Server(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) ')' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new Tk103ProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initGl100Server(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '\0' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new Gl100ProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initGl200Server(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter1[] = { (byte) '$' };
                    byte delimiter2[] = { (byte) '\0' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024,
                                    ChannelBuffers.wrappedBuffer(delimiter1),
                                    ChannelBuffers.wrappedBuffer(delimiter2)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new Gl200ProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initT55Server(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '\r', (byte) '\n' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new T55ProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initXexun2Server(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '\n' }; // tracker bug \n\r
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new Xexun2ProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initTotemServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new TotemFrameDecoder());
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new TotemProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initEnforaServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(1024, 0, 2, -2, 2));
                    pipeline.addLast("objectDecoder", new EnforaProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initMeiligaoServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new MeiligaoFrameDecoder());
                    pipeline.addLast("objectDecoder", new MeiligaoProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initMaxonServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '\r', (byte) '\n' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new MaxonProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initSuntechServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '\r' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new SuntechProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initProgressServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            TrackerServer server = new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(1024, 2, 2, 4, 0));
                    pipeline.addLast("objectDecoder", new ProgressProtocolDecoder(ServerManager.this));
                }
            };
            server.setEndianness(ByteOrder.LITTLE_ENDIAN);
            serverList.add(server);
        }
    }

    private void initH02Server(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new H02FrameDecoder());
                    pipeline.addLast("objectDecoder", new H02ProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initJt600Server(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new Jt600FrameDecoder());
                    pipeline.addLast("objectDecoder", new Jt600ProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initEv603Server(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) ';' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new Ev603ProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initV680Server(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '#', (byte) '#' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new V680ProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initPt502Server(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '\r', (byte) '\n' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new Pt502ProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initTr20Server(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '\r', (byte) '\n' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new Tr20ProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initNavisServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            TrackerServer server = new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(4 * 1024, 12, 2, 2, 0));
                    pipeline.addLast("objectDecoder", new NavisProtocolDecoder(ServerManager.this));
                }
            };
            server.setEndianness(ByteOrder.LITTLE_ENDIAN);
            serverList.add(server);
        }
    }

    private void initMeitrackServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '\r', (byte) '\n' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new MeitrackProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initSkypatrolServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ConnectionlessBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("objectDecoder", new SkypatrolProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initGt02Server(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(256, 2, 1, 2, 0));
                    pipeline.addLast("objectDecoder", new Gt02ProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initGt06Server(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new Gt06FrameDecoder());
                    pipeline.addLast("objectDecoder", new Gt06ProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initMegastekServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '\r', (byte) '\n' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new MegastekProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initNavigilServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            TrackerServer server = new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new NavigilFrameDecoder());
                    pipeline.addLast("objectDecoder", new NavigilProtocolDecoder(ServerManager.this));
                }
            };
            server.setEndianness(ByteOrder.LITTLE_ENDIAN);
            serverList.add(server);
        }
    }

    private void initGpsGateServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '\r', (byte) '\n' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new GpsGateProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initTeltonikaServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new TeltonikaFrameDecoder());
                    pipeline.addLast("objectDecoder", new TeltonikaProtocolDecoder(ServerManager.this));
                }
            });
        }
    }
    
    private void initMta6Server(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("httpDecoder", new HttpRequestDecoder());
                    pipeline.addLast("httpEncoder", new HttpResponseEncoder());
                    pipeline.addLast("objectDecoder", new Mta6ProtocolDecoder(ServerManager.this, false));
                }
            });
        }
    }

    private void initMta6CanServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("httpDecoder", new HttpRequestDecoder());
                    pipeline.addLast("httpEncoder", new HttpResponseEncoder());
                    pipeline.addLast("objectDecoder", new Mta6ProtocolDecoder(ServerManager.this, true));
                }
            });
        }
    }
    
    private void initTlt2hServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '#', (byte) '#' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(32 * 1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new Tlt2hProtocolDecoder(ServerManager.this));
                }
            });
        }
    }
    
    private void initSyrusServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '<' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new SyrusProtocolDecoder(ServerManager.this, true));
                }
            });
        }
    }

    private void initWondexServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new WondexFrameDecoder());
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new WondexProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initCellocatorServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            TrackerServer server = new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new CellocatorFrameDecoder());
                    pipeline.addLast("objectDecoder", new CellocatorProtocolDecoder(ServerManager.this));
                }
            };
            server.setEndianness(ByteOrder.LITTLE_ENDIAN);
            serverList.add(server);
        }
    }

    private void initGalileoServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            TrackerServer server = new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new GalileoFrameDecoder());
                    pipeline.addLast("objectDecoder", new GalileoProtocolDecoder(ServerManager.this));
                }
            };
            server.setEndianness(ByteOrder.LITTLE_ENDIAN);
            serverList.add(server);
        }
    }

    private void initYwtServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '\r', (byte) '\n' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new YwtProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initTk102Server(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) ']' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new Tk102ProtocolDecoder(ServerManager.this));
                }
            });
        }
    }
    
    private void initIntellitracServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new IntellitracFrameDecoder(1024));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new IntellitracProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initXt7Server(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(256, 20, 1, 5, 0));
                    pipeline.addLast("objectDecoder", new Xt7ProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initWialonServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LineBasedFrameDecoder(1024));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new WialonProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initCarscopServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '^' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new CarscopProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initApelServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            TrackerServer server = new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(1024, 2, 2, 4, 0));
                    pipeline.addLast("objectDecoder", new ApelProtocolDecoder(ServerManager.this));
                }
            };
            server.setEndianness(ByteOrder.LITTLE_ENDIAN);
            serverList.add(server);
        }
    }

    private void initManPowerServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) ';' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new ManPowerProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initGlobalSatServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '!' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new GlobalSatProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initAtrackServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new AtrackFrameDecoder());
                    pipeline.addLast("objectDecoder", new AtrackProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initPt3000Server(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) 'd' }; // probably wrong
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new Pt3000ProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initRuptelaServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(1024, 0, 2, 2, 0));
                    pipeline.addLast("objectDecoder", new RuptelaProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initTopflytechServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) ')' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new TopflytechProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initLaipacServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LineBasedFrameDecoder(1024));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new LaipacProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initAplicomServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new AplicomFrameDecoder());
                    pipeline.addLast("objectDecoder", new AplicomProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initGotopServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '#' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new GotopProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initSanavServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '*' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new SanavProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initGatorServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ConnectionlessBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("objectDecoder", new GatorProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initNoranServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            TrackerServer server = new TrackerServer(this, new ConnectionlessBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("objectDecoder", new NoranProtocolDecoder(ServerManager.this));
                }
            };
            server.setEndianness(ByteOrder.LITTLE_ENDIAN);
            serverList.add(server);
        }
    }

    private void initM2mServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new FixedLengthFrameDecoder(23));
                    pipeline.addLast("objectDecoder", new M2mProtocolDecoder(ServerManager.this));
                }
            });
        }
    }
    
    private void initOsmAndServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("httpDecoder", new HttpRequestDecoder());
                    pipeline.addLast("httpEncoder", new HttpResponseEncoder());
                    pipeline.addLast("objectDecoder", new OsmAndProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initEasyTrackServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '#' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new EasyTrackProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initTaipServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ConnectionlessBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new SyrusProtocolDecoder(ServerManager.this, false));
                }
            });
        }
    }

    private void initKhdServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(256, 3, 2));
                    pipeline.addLast("objectDecoder", new KhdProtocolDecoder(ServerManager.this));
                }
            });
        }
    }
    
    private void initPiligrimServer(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("httpDecoder", new HttpRequestDecoder());
                    pipeline.addLast("httpAggregator", new HttpChunkAggregator(16384));
                    pipeline.addLast("httpEncoder", new HttpResponseEncoder());
                    pipeline.addLast("objectDecoder", new PiligrimProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

    private void initStl060Server(String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new Stl060FrameDecoder(1024));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new Stl060ProtocolDecoder(ServerManager.this));
                }
            });
        }
    }

}
