package com.droid2developers.liveslider.database.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.io.Serializable;

@Entity
public class LocalWallpaper implements Serializable {

    @PrimaryKey(autoGenerate = true)
    private int id;
    private String playlistId;
    private String name;
    private String localPath;
    private String originalPath;
    /** Horizontal crop bias in renderer world units. 0 = center crop (default).
     *  Positive shows more of the image's left side. Set via the triple-tap
     *  crop overlay; applied as a camera-x offset, clamped to the image's slack. */
    private float cropBias = 0f;

    public LocalWallpaper(String playlistId, String name, String localPath, String originalPath) {
        this.playlistId = playlistId;
        this.name = name;
        this.localPath = localPath;
        this.originalPath = originalPath;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(String playlistId) {
        this.playlistId = playlistId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public String getOriginalPath() {
        return originalPath;
    }

    public void setOriginalPath(String originalPath) {
        this.originalPath = originalPath;
    }

    public float getCropBias() {
        return cropBias;
    }

    public void setCropBias(float cropBias) {
        this.cropBias = cropBias;
    }

    @Override
    public String toString() {
        return "LocalWallpaper{" +
                "id=" + id +
                ", playlistId='" + playlistId + '\'' +
                ", name='" + name + '\'' +
                ", localPath='" + localPath + '\'' +
                ", originalPath='" + originalPath + '\'' +
                '}';
    }
}
