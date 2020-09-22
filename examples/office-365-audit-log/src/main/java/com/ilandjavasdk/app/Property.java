package com.ilandjavasdk.app;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Property {

  private static final String CLIENT_ID;

  private static final String CLIENT_SECRET;

  private static final String USERNAME;

  private static final String PASSWORD;

  private static final Properties prop = new Properties();

  static {
    try {
      final InputStream in = Thread.currentThread().getClass()
          .getResourceAsStream("/app.properties");
      prop.load(in);
      in.close();
    } catch (final IOException e) {
      e.printStackTrace();
    }
    USERNAME = prop.getProperty("username");
    PASSWORD = prop.getProperty("password");
    CLIENT_ID = prop.getProperty("client.id");
    CLIENT_SECRET = prop.getProperty("client.secret");
  }

  public static String getClientId() {
    return CLIENT_ID;
  }

  public static String getClientSecret() {
    return CLIENT_SECRET;
  }

  public static String getUsername() {
    return USERNAME;
  }

  public static String getPassword() {
    return PASSWORD;
  }

}
