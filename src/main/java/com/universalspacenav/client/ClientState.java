package com.universalspacenav.client;

import com.universalspacenav.NavMode;

public final class ClientState {
    public static volatile NavMode mode = NavMode.ARCANA;

    public static volatile int protection = 0;

    private ClientState() {
    }
}
