package com.ilandjavasdk.app;

import java.util.List;
import java.util.Map;

import com.iland.core.api.iam.IamEntityType;
import com.iland.core.web.rest.api.CompanyLocationResource;
import com.iland.core.web.rest.api.CompanyResource;
import com.iland.core.web.rest.api.UserResource;
import com.iland.core.web.rest.response.iam.UserInventoryEntityResponse;
import com.iland.core.web.rest.response.user.UserCompanyInventoryResponse;
import com.iland.core.web.rest.response.user.UserInventoryResponse;
import com.iland.core.web.rest.response.user.UserResponse;
import com.iland.core.web.rest.response.vbo.O365AuditLogEventResponse;
import com.iland.core.web.rest.response.vbo.O365AuditLogEventSetResponse;
import com.iland.core.web.sdk.Client;
import com.iland.core.web.sdk.connection.OAuthException;

/**
 * Print the Office 365 audit log.
 */
public class App {

  private static String companyId = "";

  private static String locationId = "";

  private static String o365Location = "";

  private static final Client apiClient;

  private static final UserResource userResource;

  private static final CompanyLocationResource companyLocationResource;

  private static final Property property;

  static {
    property = new Property();
    apiClient = getApiClient();
    userResource = apiClient.getUserResource();
    companyLocationResource = apiClient.getCompanyLocationResource();
  }

  /**
   * Logs into account provided in the app.properties file.
   * Gets the first Office 365 location which contains the company id as well.
   * Prints out the first 100 entries in the Office 365 audit log.
   */
  public static void main(String[] args) {
    login();
    final UserResponse user = userResource.getUser(Property.getUsername());
    getOffice365Location(user);
    if (!o365Location.isEmpty()) {
      final String[] splitO365Location = o365Location.split(":");
      companyId = splitO365Location[3];
      locationId = splitO365Location[5];
      printOffice365AuditLog();
    }
    logout();
  }

  /**
   * Get's the Office 365 location for the given user by looking
   * through their inventory.
   *
   * @param user {@link UserResponse} user
   */
  private static void getOffice365Location(final UserResponse user) {
    final UserInventoryResponse userInventory =
        userResource.getInventory(user.getName(), null);
    for (final UserCompanyInventoryResponse companyInventoryResponse : userInventory
        .getInventory()) {
      final Map<IamEntityType, List<UserInventoryEntityResponse>> entities =
          companyInventoryResponse.getEntities();
      for (final IamEntityType entity : entities.keySet()) {
        for (final UserInventoryEntityResponse e : entities.get(entity)) {
          if (e.getType().equals(IamEntityType.O365_LOCATION)) {
            o365Location = e.getUuid();
            break;
          }
        }
      }
    }
  }

  /**
   * Get and and print the Office 365 audit log.
   *
   * Here we are only getting the first 100 entries, to get more you would provide
   * the correct paging parameters.
   */
  private static void printOffice365AuditLog() {
    int page = 0;
    int pageSize = 100;
    O365AuditLogEventSetResponse auditLogEventResponses =
        companyLocationResource
            .getO365AuditLog(companyId, locationId, page, pageSize);
    for (final O365AuditLogEventResponse logEventResponse : auditLogEventResponses
        .getData()) {
      System.out.println(String.format(
          "User %s at IP: %s did event %s for entity %s of type %s at time %s.",
          logEventResponse.getUsername(), logEventResponse.getIpAddress(),
          logEventResponse.getEventType(), logEventResponse.getEntityName(),
          logEventResponse.getEventType(), logEventResponse.getTime()));
    }
  }

  /**
   * Gets a Client.
   *
   * @return {@link Client} api client
   */
  private static Client getApiClient() {
    return new Client(Property.getClientId(), Property.getClientSecret());
  }

  /**
   * Login into the Client.
   */
  private static void login() {
    try {
      apiClient.login(Property.getUsername(), Property.getPassword());
    } catch (final OAuthException e) {
      e.printStackTrace();
    }
  }

  /**
   * Logs out of the Client.
   */
  private static void logout() {
    apiClient.logout();
  }

}
