package com.imaginarycode.minecraft.redisbungee.api.config.loaders;

import ninja.leaping.configurate.SimpleConfigurationNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProxyMetadataConfigTest {
    @Test
    void optionalMapAndEnvironmentExpansion() {
        var root = SimpleConfigurationNode.root();
        assertEquals(Map.of(), ConfigLoader.readProxyMetadata(root));
        root.getNode("proxy-metadata", "voice-endpoint").setValue("${XMINE_METADATA_TEST_MISSING:-proxy.example:24454}");
        root.getNode("proxy-metadata", "empty").setValue("");
        root.getNode("proxy-metadata", "literal").setValue("$${LITERAL}");
        assertEquals(Map.of("voice-endpoint", "proxy.example:24454", "literal", "${LITERAL}"), ConfigLoader.readProxyMetadata(root));
    }

    @Test
    void rejectsNonStringNodes() {
        for (Object value : new Object[]{42, Map.of("nested", "value")}) {
            var root = SimpleConfigurationNode.root();
            root.getNode("proxy-metadata", "key").setValue(value);
            assertThrows(IllegalArgumentException.class, () -> ConfigLoader.readProxyMetadata(root));
        }
        var root = SimpleConfigurationNode.root();
        root.getNode("proxy-metadata").setValue("bad");
        assertThrows(IllegalArgumentException.class, () -> ConfigLoader.readProxyMetadata(root));
    }
}
