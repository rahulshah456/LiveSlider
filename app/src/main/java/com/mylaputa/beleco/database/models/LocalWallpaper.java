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
    private int playlistId;
    private String name;
    private Uri originalPath;

    public LocalWallpaper(int id, int playlistId, String name, Uri originalPath) {
        this.id = id;
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

    public int getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(int playlistId) {
        this.playlistId = playlistId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Uri getOriginalPath() {
        return originalPath;
    }

    public void setOriginalPath(Uri originalPath) {
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
