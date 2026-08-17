package com.artillexstudios.axgraves.grave.placement;

/** What a single {@link BlockProbe#probe(int, int, int)} call found at a coordinate. */
public enum ProbeResult {
    /** Non-solid, non-hazardous - fine for a grave to occupy. */
    SAFE,
    /** A solid block - the grave would be embedded in it. */
    SOLID,
    /** Lava (or another instant-damage liquid). */
    HAZARD,
    /** The containing chunk is not loaded; never probed by loading it. */
    UNLOADED,
    /** Outside the world's vertical bounds. */
    OUT_OF_BOUNDS
}
