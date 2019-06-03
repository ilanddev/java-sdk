package com.iland.app;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.iland.core.model.EntityType;
import com.iland.core.web.rest.api.EventResource;
import com.iland.core.web.rest.response.event.EventFilterParams;
import com.iland.core.web.rest.response.event.EventListResponse;
import com.iland.core.web.rest.response.event.EventResponse;
import com.iland.core.web.rest.response.event.EventType;
import com.iland.core.web.sdk.Client;
import com.iland.core.web.sdk.connection.OAuthException;

/**
 * Example iland Java SDK app that demonstrates how to get and process events.
 */
public class App {

  final static Log log = LogFactory.getLog(App.class);

  final static String USERNAME = "";
  final static String PASSWORD = "";
  final static String CLIENT_NAME = "";
  final static String CLIENT_SECRET = "";

  static Client apiClient = new Client(CLIENT_NAME, CLIENT_SECRET);

  static EventResource eventResource;

  static EventType[] vmEventTypes =
      new EventType[] {EventType.VM_ANTIMALWARE_EVENT, EventType.VM_DPI_EVENT,
          EventType.VM_FIREWALL_EVENT, EventType.VM_INTEGRITY_EVENT,
          EventType.VM_LOG_INSPECTION_EVENT, EventType.VM_WEB_REPUTATION_EVENT};

  static EventType[] orgEventTypes =
      new EventType[] {EventType.ORG_VULNERABILITY_SCAN_LAUNCH,
          EventType.ORG_VULNERABILITY_SCAN_PAUSE,
          EventType.ORG_VULNERABILITY_SCAN_RESUME,
          EventType.ORG_VULNERABILITY_SCAN_STOP};

  static final String COMPANY_ID = "";

  public static void main(String[] args) {
    authentication();
    eventResource = apiClient.getEventResource();
    getAndProcessEvents();
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
   * In this function we continuously get the events of a company every 5 seconds
   * and check to see if they are the ones we want and process them by logging.
   */
  static void getAndProcessEvents() {

    final Set<EventType> vmEvents = new HashSet<>(Arrays.asList(vmEventTypes));
    final Set<EventType> orgEvents =
        new HashSet<>(Arrays.asList(orgEventTypes));

    while (true) {
      try {
        final EventFilterParams eventFilterParams =
            new EventFilterParams(EntityType.COMPANY, COMPANY_ID);
        final Instant currentTime = Instant.now();
        final Instant fiveSecondsAgo = Instant.now().minus(5, ChronoUnit.SECONDS);
        eventFilterParams.setIncludeDescendantEvents(true);
        eventFilterParams.setTimestampAfter(fiveSecondsAgo.toEpochMilli());
        eventFilterParams.setTimestampBefore(currentTime.toEpochMilli());

        final EventListResponse events = eventResource
            .getEvents(eventFilterParams);

        for (final EventResponse event : events.getData()) {
          if (EntityType.IAAS_VM.equals(event.getEntityType()) && vmEvents
              .contains(event.getType())
              || EntityType.IAAS_ORGANIZATION.equals(event.getEntityType())
              && orgEvents.contains(event.getType())) {
            log.info(String.format("User %s initiated event %s for entity %s",
                event.getInitiatedByUsername(), event.getType(),
                event.getEntityName()));
          }
        }
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
      } catch (final InterruptedException e) {
        log.error(e.getMessage());
      }
    }
  }

}


