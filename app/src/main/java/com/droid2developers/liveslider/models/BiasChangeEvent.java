package com.droid2developers.liveslider.models;

public class BiasChangeEvent {
    float x, y;

    public BiasChangeEvent(float x, float y) {
        if (x > 1) this.x = 1;
        else if (x < -1) this.x = -1;
        else this.x = x;
        if (y > 1) this.y = 1;
        else if (y < -1) this.y = -1;
        else this.y = y;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }
}