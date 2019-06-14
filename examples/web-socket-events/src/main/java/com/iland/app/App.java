package com.iland.app;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.iland.core.api.iam.IamEntityType;
import com.iland.core.web.rest.api.UserResource;
import com.iland.core.web.rest.response.event.EventResponse;
import com.iland.core.web.rest.response.event.EventType;
import com.iland.core.web.rest.response.iam.UserInventoryEntityResponse;
import com.iland.core.web.rest.response.user.UserCompanyInventoryResponse;
import com.iland.core.web.rest.response.user.UserInventoryResponse;
import com.iland.core.web.sdk.Client;
import com.iland.core.web.sdk.connection.OAuthException;
import com.iland.core.web.sdk.connection.WebSocketClient;
import com.iland.core.web.sdk.util.EventFilter;
import com.iland.core.web.sdk.util.SocketSubscription;

/**
 * Connect to an event web socket and process events.
 *
 */
public class App {

  static Log log = LogFactory.getLog(App.class);

  final static String USERNAME = "";
  final static String PASSWORD = "";
  final static String CLIENT_NAME = "";
  final static String CLIENT_SECRET = "";

  static String COMPANY_ID = "";

  static String ORG_UUID = "";

  static Client apiClient = new Client(CLIENT_NAME, CLIENT_SECRET);

  static EventType[] vmEventTypes =
      new EventType[] {EventType.VM_ANTIMALWARE_EVENT, EventType.VM_DPI_EVENT,
          EventType.VM_FIREWALL_EVENT, EventType.VM_INTEGRITY_EVENT,
          EventType.VM_LOG_INSPECTION_EVENT, EventType.VM_WEB_REPUTATION_EVENT};

  static EventType[] orgEventTypes =
      new EventType[] {EventType.ORG_VULNERABILITY_SCAN_LAUNCH,
          EventType.ORG_VULNERABILITY_SCAN_PAUSE,
          EventType.ORG_VULNERABILITY_SCAN_RESUME,
          EventType.ORG_VULNERABILITY_SCAN_STOP};

  static UserResource userResource;

  public static void main(String[] args) {
      authentication();
      ORG_UUID = setCompanyIdAndGetOrgUuid();
      webSocketProcessEvents();
  }

  /**
   * Basic authentication using iland's Client object.
   */
  static void authentication() {
    try {
      apiClient.login(USERNAME, PASSWORD);
    } catch (final OAuthException e) {
      System.out.println(e.getMessage());
    }
  }

  /**
   * Lazily grab a org uuid from a user's inventory.
   */
  static String setCompanyIdAndGetOrgUuid() {
    String orgUuid = "";
    UserInventoryResponse userInventory =
        userResource.getInventory(USERNAME, null);
    for (UserCompanyInventoryResponse companyInventory : userInventory
        .getInventory()) {
      COMPANY_ID = companyInventory.getCompanyId();
      if (companyInventory.getEntities() != null && !companyInventory
          .getEntities().isEmpty()) {
        List<UserInventoryEntityResponse> companies =
            companyInventory.getEntities().get(IamEntityType.COMPANY);
        for (UserInventoryEntityResponse company : companies) {
          if (orgUuid.isEmpty()) {
            orgUuid = company.getUuid();
            break;
          }
        }
      }
    }
    return orgUuid;
  }

  /**
   * This function connects to a web socket and gets events based on the type
   * we specify. We use two different examples of how to filter events. The
   * first way is using a Set of EventTypes and the second way is to use
   * a custom EventFilter. We need to keep the process alive thus the need for
   * the while loop. Note if we want to not consume at some point in time
   * we can use the unSubscribe() function in the SocketSubscription.
   */
  static void webSocketProcessEvents() {
    final WebSocketClient webSocketClient =
        apiClient.getEventWebSocket(COMPANY_ID);

    final Set<EventType> vmTypes = new HashSet<>(Arrays.asList(vmEventTypes));
     Consumer<EventResponse> vmEventConsumer = c -> log.info(String
        .format("User %s initiated vm event %s for entity %s",
            c.getInitiatedByUsername(), c.getType(), c.getEntityName()));
    final SocketSubscription vmSubscription =
        webSocketClient.consumeEvents(vmEventConsumer, vmTypes);

    final Set<EventType> orgTypes = new HashSet<>(Arrays.asList(orgEventTypes));
    final EventFilter orgEventFilter =
        eventResponse -> orgTypes.contains(eventResponse.getType())
            && eventResponse.getEntityUuid().equals(ORG_UUID);
    final Consumer<EventResponse> orgEventConsumer = c -> log.info(String
        .format("User %s initiated org event %s for entity %s",
            c.getInitiatedByUsername(), c.getType(), c.getEntityName()));
    final SocketSubscription orgSubscription =
        webSocketClient.consumeEvents(orgEventConsumer, orgEventFilter);

    // Keeping the process alive so the web socket can consume events.
    // Exit if interrupted exception is caught.
    try {
      while (true) {
        Thread.sleep(TimeUnit.MINUTES.toMillis(5));
      }
    } catch (final InterruptedException e) {
      System.exit(0);
    }
  }
  
  
}
