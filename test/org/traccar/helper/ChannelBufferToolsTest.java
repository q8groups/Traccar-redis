package org.traccar.helper;

import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.HeapChannelBufferFactory;
import static org.junit.Assert.*;
import org.junit.Test;

public class ChannelBufferToolsTest {
    
    private ChannelBufferFactory factory = new HeapChannelBufferFactory();
    
    @Test
    public void testFind() {
    }

    @Test
    public void testReadHexInteger() {
        byte[] buf = {0x01,(byte)0x90,0x34};
        int result = ChannelBufferTools.readHexInteger(
                factory.getBuffer(buf, 0, buf.length), 5);
        assertEquals(1903, result);
    }
    
    @Test
    public void testReadHexString() {
        byte[] buf = {0x01,(byte)0x90,0x34};
        String result = ChannelBufferTools.readHexString(
                factory.getBuffer(buf, 0, buf.length), 5);
        assertEquals("01903", result);
        
        result = Long.valueOf(result).toString();
        assertEquals("1903", result);
    }
}
