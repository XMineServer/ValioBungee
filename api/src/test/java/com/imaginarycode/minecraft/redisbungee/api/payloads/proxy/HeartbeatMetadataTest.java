package com.imaginarycode.minecraft.redisbungee.api.payloads.proxy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.imaginarycode.minecraft.redisbungee.api.payloads.proxy.gson.HeartbeatPayloadSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HeartbeatMetadataTest {
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(HeartbeatPayload.class, new HeartbeatPayloadSerializer()).create();

    @Test
    void readsExistingHeartbeatWithoutMetadata() {
        var payload = gson.fromJson("{\"proxy\":\"velocity-1\",\"heartbeat\":123,\"players\":7}", HeartbeatPayload.class);
        assertEquals("velocity-1", payload.senderProxy());
        assertEquals(123, payload.data().heartbeat());
        assertEquals(7, payload.data().players());
        assertEquals(Map.of(), payload.data().metadata());
        assertEquals(Map.of(), new HeartbeatPayload.HeartbeatData(123, 7).metadata());
    }

    @Test
    void addsMetadataWithoutChangingExistingWireFields() {
        Map<String, String> metadata = Map.of("voice-endpoint", "[fd00::2]:24454", "region", "ru");
        var payload = new HeartbeatPayload("velocity-2", new HeartbeatPayload.HeartbeatData(456, 8, metadata));
        String wire = gson.toJson(payload);
        var json = JsonParser.parseString(wire).getAsJsonObject();
        assertEquals("velocity-2", json.get("proxy").getAsString());
        assertEquals(456, json.get("heartbeat").getAsLong());
        assertEquals(8, json.get("players").getAsInt());
        assertEquals("[fd00::2]:24454", json.getAsJsonObject("metadata").get("voice-endpoint").getAsString());
        assertEquals(payload.data(), gson.fromJson(wire, HeartbeatPayload.class).data());
    }
}
