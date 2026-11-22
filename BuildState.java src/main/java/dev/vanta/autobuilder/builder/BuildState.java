package dev.vanta.autobuilder.builder;

public enum BuildState {
    IDLE,
    EATING,
    GOING_TO_CHEST,
    FETCHING_FROM_CHEST,
    MOVING_TO_POSITION,
    BUILDING_SCAFFOLD,
    PLACING_BLOCK,
    BREAKING_WRONG_BLOCK,
    REMOVING_SCAFFOLD,
    UNSTUCK,
    DONE
}
