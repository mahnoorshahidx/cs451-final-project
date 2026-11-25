package com.tripbudgetplanner.backend.search;

// What one unitPrice pays for. estimatedTotal = unitPrice x quantity is always for the whole group.
public enum PriceBasis {

    PER_PERSON_ROUND_TRIP("per person, round trip"),
    PER_ROOM_PER_NIGHT("per room, per night"),
    PER_PERSON("per person"),
    PER_GROUP("for the whole group");

    private final String label;

    PriceBasis(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
