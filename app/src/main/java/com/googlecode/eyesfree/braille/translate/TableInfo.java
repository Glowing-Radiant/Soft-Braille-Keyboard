package com.googlecode.eyesfree.braille.translate;

import android.os.Parcel;
import android.os.Parcelable;

public class TableInfo implements Parcelable {
    private String id;
    private String locale;
    
    public TableInfo() {
        this.id = "";
        this.locale = "en-US";
    }
    
    public TableInfo(String id) {
        this.id = id;
        this.locale = "en-US";
    }
    
    public String getId() {
        return id;
    }
    
    public String getLocale() {
        return locale;
    }
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(locale);
    }
    
    public static final Parcelable.Creator<TableInfo> CREATOR = new Parcelable.Creator<TableInfo>() {
        public TableInfo createFromParcel(Parcel in) {
            TableInfo info = new TableInfo();
            info.id = in.readString();
            info.locale = in.readString();
            return info;
        }
        
        public TableInfo[] newArray(int size) {
            return new TableInfo[size];
        }
    };
}
