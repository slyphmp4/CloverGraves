package com.artillexstudios.axgraves.storage;

/** Why a grave stopped being live, recorded alongside its history entry. */
public enum EndReason {
    LOOTED,
    EXPIRED,
    REMOVED,
    LIMIT,
    SHUTDOWN,
    RESTORED
}
