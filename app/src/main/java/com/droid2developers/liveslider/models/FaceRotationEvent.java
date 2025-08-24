package com.droid2developers.liveslider.models;

import com.droid2developers.liveslider.utils.Constant;

public class FaceRotationEvent {
    private final int face;
    private final String readableFaceName;

    public FaceRotationEvent(int face) {
        this.face = face;
        this.readableFaceName = Constant.getFaceNameReadable(face);
    }

    public int getFace() {
        return face;
    }

    public String getReadableFaceName() {
        return readableFaceName;
    }
}
