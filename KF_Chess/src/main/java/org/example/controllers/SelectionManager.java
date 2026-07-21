package org.example.controllers;

import org.example.models.Position;

public class SelectionManager {
    private boolean selected = false;
    private Position selectedPosition = new Position(-1, -1);

    public boolean isSelected() {
        return selected;
    }

    public Position getSelectedPosition() {
        return selectedPosition;
    }

    public void select(Position position) {
        this.selected = true;
        this.selectedPosition = position;
    }

    public void clear() {
        this.selected = false;
        this.selectedPosition = new Position(-1, -1);
    }
}