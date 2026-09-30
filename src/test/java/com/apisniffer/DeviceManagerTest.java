package com.apisniffer;

import com.apisniffer.device.DeviceInfo;
import com.apisniffer.device.DeviceManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DeviceManagerTest {

    @Test
    void testRecordActivityAndNaming() {
        DeviceManager manager = new DeviceManager();

        // 1. Android phone activity
        String androidUa = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UQ1A.231205.015) AppleWebKit/537.36";
        DeviceInfo d1 = manager.recordActivity("192.168.137.50", androidUa);

        assertNotNull(d1);
        assertEquals("192.168.137.50", d1.getIp());
        assertTrue(d1.isHotspot());
        assertTrue(d1.getName().contains("Pixel 7") || d1.getName().contains("Android"));
        assertEquals(1, d1.getRequestCount());

        // 2. Second request from same device increments count
        manager.recordActivity("192.168.137.50", androidUa);
        assertEquals(2, d1.getRequestCount());

        // 3. Regular LAN client
        String pcUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0";
        DeviceInfo d2 = manager.recordActivity("192.168.1.100", pcUa);
        assertNotNull(d2);
        assertFalse(d2.isHotspot());
        assertTrue(d2.getName().contains("Windows"));

        // 4. Listing devices (hotspot devices should be prioritized)
        List<DeviceInfo> list = manager.getAllDevices();
        assertTrue(list.size() >= 2);
        assertTrue(list.get(0).isHotspot());
    }

    @Test
    void testInferDeviceName() {
        assertEquals("Pixel 7 (Android 14)",
                DeviceManager.inferDeviceName("192.168.137.10", "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/XYZ)", true));

        assertTrue(DeviceManager.inferDeviceName("192.168.1.5", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X)", false)
                .contains("iPhone"));
    }
}
