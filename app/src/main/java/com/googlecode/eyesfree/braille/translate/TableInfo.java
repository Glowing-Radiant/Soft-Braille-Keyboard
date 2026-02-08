package com.googlecode.eyesfree.braille.translate;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Locale;

public class TableInfo implements Parcelable {
    private String id;
    private Locale locale;
    private int grade;
    private boolean eightDot;
    
    public TableInfo() {
        this.id = "";
        this.locale = Locale.US;
        this.grade = 1;
        this.eightDot = false;
    }
    
    public TableInfo(String id) {
        this.id = id;
        this.locale = Locale.US;
        this.grade = 1;
        this.eightDot = false;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public Locale getLocale() {
        return locale;
    }
    
    public void setLocale(Locale locale) {
        this.locale = locale;
    }
    
    public int getGrade() {
        return grade;
    }
    
    public void setGrade(int grade) {
        this.grade = grade;
    }
    
    public boolean isEightDot() {
        return eightDot;
    }
    
    public void setEightDot(boolean eightDot) {
        this.eightDot = eightDot;
    }
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(locale != null ? locale.toString() : "en_US");
        dest.writeInt(grade);
        dest.writeByte((byte) (eightDot ? 1 : 0));
    }
    
    public static final Parcelable.Creator<TableInfo> CREATOR = new Parcelable.Creator<TableInfo>() {
        public TableInfo createFromParcel(Parcel in) {
            TableInfo info = new TableInfo();
            info.id = in.readString();
            String localeStr = in.readString();
            info.locale = localeStr != null ? new Locale(localeStr) : Locale.US;
            info.grade = in.readInt();
            info.eightDot = in.readByte() != 0;
            return info;
        }
        
        public TableInfo[] newArray(int size) {
            return new TableInfo[size];
        }
    };
}
