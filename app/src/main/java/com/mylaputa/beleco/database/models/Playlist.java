package com.mylaputa.beleco.database.models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;
import com.mylaputa.beleco.utils.TimestampConverter;

import java.io.Serializable;
import java.util.Date;

@Entity
public class Playlist implements Serializable {

    @PrimaryKey
    private String playlistId;
    private int name;
    private int description;

    @ColumnInfo(name = "created_at")
    @TypeConverters({TimestampConverter.class})
    private Date createdAt;

    @ColumnInfo(name = "modified_at")
    @TypeConverters({TimestampConverter.class})
    private Date modifiedAt;


    public Playlist(String playlistId, int name, int description, Date createdAt, Date modifiedAt) {
        this.playlistId = playlistId;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
    }

    public String getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(String playlistId) {
        this.playlistId = playlistId;
    }

    public int getName() {
        return name;
    }

    public void setName(int name) {
        this.name = name;
    }

    public int getDescription() {
        return description;
    }

    public void setDescription(int description) {
        this.description = description;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(Date modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    @NonNull
    @Override
    public String toString() {
        return "Playlist{" +
                "id=" + playlistId +
                ", name=" + name +
                ", description=" + description +
                ", createdAt=" + createdAt +
                ", modifiedAt=" + modifiedAt +
                '}';
    }
}
