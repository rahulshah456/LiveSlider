package com.mylaputa.beleco.database.models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.mylaputa.beleco.utils.StringListConverter;
import com.mylaputa.beleco.utils.TimestampConverter;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Entity
public class Playlist implements Serializable {

    @PrimaryKey
    @NonNull
    private String playlistId;
    private String name;

    @TypeConverters(StringListConverter.class)
    private List<String> cover_urls;

    @ColumnInfo(name = "created_at")
    @TypeConverters({TimestampConverter.class})
    private Date createdAt;

    @ColumnInfo(name = "modified_at")
    @TypeConverters({TimestampConverter.class})
    private Date modifiedAt;
    private int size;


    public Playlist(@NonNull String playlistId, String name, List<String> cover_urls,
                    Date createdAt, Date modifiedAt, int size) {
        this.playlistId = playlistId;
        this.name = name;
        this.cover_urls = cover_urls;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
        this.size = size;
    }

    @NonNull
    public String getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(@NonNull String playlistId) {
        this.playlistId = playlistId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public List<String> getCover_urls() {
        return cover_urls;
    }

    public void setCover_urls(List<String> cover_urls) {
        this.cover_urls = cover_urls;
    }

    @Override
    public String toString() {
        return "Playlist{" +
                "playlistId='" + playlistId + '\'' +
                ", name='" + name + '\'' +
                ", cover_urls=" + cover_urls +
                ", createdAt=" + createdAt +
                ", modifiedAt=" + modifiedAt +
                ", size=" + size +
                '}';
    }
}
