package com.universalspacenav.client;

import com.universalspacenav.NavMode;

public final class ClientState {
    public static volatile NavMode mode = NavMode.ARCANA;

    public static volatile int protection = 0;
    public static volatile int bits = 0;
    public static volatile int reserve = -1;
    public static volatile int reserveMax = 0;
    public static volatile int suitEnergy = -1;
    public static volatile int exoStored = -1;
    public static volatile int exoCap = 0;

    private ClientState() {
    }
}
