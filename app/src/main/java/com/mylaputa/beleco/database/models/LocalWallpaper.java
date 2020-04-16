package com.mylaputa.beleco.database.models;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.io.Serializable;

@Entity
public class LocalWallpaper implements Serializable {

    @PrimaryKey(autoGenerate = true)
    private int id;
    private String playlistId;
    private String name;
    private String originalPath;

    public LocalWallpaper(String playlistId, String name, String originalPath) {
        this.playlistId = playlistId;
        this.name = name;
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

    public String getOriginalPath() {
        return originalPath;
    }

    public void setOriginalPath(String originalPath) {
        this.originalPath = originalPath;
    }

    @NonNull
    @Override
    public String toString() {
        return "LocalWallpaper{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", originalPath=" + originalPath +
                '}';
    }
}
