package com.jadaptive.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class HandleRegistrySocketTest {

    @Test
    void closesRegisteredSocketHandles() throws Exception {
        HandleRegistry registry = new HandleRegistry();
        String socketHandle = registry.registerSocket(new Socket());

        assertTrue(registry.closeSocket(socketHandle));
        assertFalse(registry.closeSocket(socketHandle));
    }

    @Test
    void closesRegisteredListenerHandlesAndExposesSnapshot() throws Exception {
        HandleRegistry registry = new HandleRegistry();
        ServerSocket listener = new ServerSocket(0);
        String listenerHandle = registry.registerSocketListener(listener);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> listeners = (List<Map<String, Object>>) registry.snapshot().get("socketlistener");
        assertEquals(1, listeners.size());

        assertTrue(registry.closeSocketListener(listenerHandle));
        assertFalse(registry.closeSocketListener(listenerHandle));
    }
}
