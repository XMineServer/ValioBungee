/*
 * Copyright (c) 2013-present RedisBungee contributors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 *
 *  http://www.eclipse.org/legal/epl-v10.html
 */

package com.imaginarycode.minecraft.redisbungee.api.payloads.proxy;

import com.imaginarycode.minecraft.redisbungee.api.payloads.AbstractPayload;

import java.util.Map;

public class HeartbeatPayload extends AbstractPayload {

    public record HeartbeatData(long heartbeat, int players, Map<String, String> metadata) {
        public HeartbeatData {
            metadata = Map.copyOf(metadata);
        }

        public HeartbeatData(long heartbeat, int players) {
            this(heartbeat, players, Map.of());
        }
    }

    private final HeartbeatData data;

    public HeartbeatPayload(String proxyId, HeartbeatData data) {
        super(proxyId);
        this.data = data;
    }

    public HeartbeatData data() {
        return data;
    }
}
