package com.universalspacenav.client;

import com.universalspacenav.NavMode;

public final class ClientState {
    public static volatile NavMode mode = NavMode.ARCANA;

    private ClientState() {
    }
}
