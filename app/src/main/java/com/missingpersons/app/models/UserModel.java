package com.missingpersons.app.models;

public class UserModel {
    private String uid;
    private String name;
    private String email;
    private String whatsapp;
    private String fcmToken;

    public UserModel() {}

    public UserModel(String uid, String name, String email, String whatsapp) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.whatsapp = whatsapp;
    }

    public String getUid() { return uid; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getWhatsapp() { return whatsapp; }
    public String getFcmToken() { return fcmToken; }

    public void setUid(String uid) { this.uid = uid; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setWhatsapp(String whatsapp) { this.whatsapp = whatsapp; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }
}
