package com.ilandjavasdk.app;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iland.core.api.entity.EntityTreeNode;
import com.iland.core.api.entity.EntityType;
import com.iland.core.api.entity.OrgEntityTree;
import com.iland.core.api.net.IpAddressingMode;
import com.iland.core.api.net.IpRange;
import com.iland.core.api.net.VappNetworkInitializationParams;
import com.iland.core.api.task.CoreTask;
import com.iland.core.api.vcd.CatalogUploadVappTemplateSpec;
import com.iland.core.api.vcd.FenceMode;
import com.iland.core.api.vcd.User;
import com.iland.core.api.vcd.Vapp;
import com.iland.core.api.vcd.VappSpec;
import com.iland.core.api.vcd.VappTemplate;
import com.iland.core.api.vcd.VappTemplateVm;
import com.iland.core.api.vcd.Vm;
import com.iland.core.api.vcd.VmSpec;
import com.iland.core.web.rest.api.CatalogResource;
import com.iland.core.web.rest.api.TaskResource;
import com.iland.core.web.rest.api.UserResource;
import com.iland.core.web.rest.api.VappResource;
import com.iland.core.web.rest.api.VappTemplateResource;
import com.iland.core.web.rest.api.VcdCatalogVappTemplateUpload;
import com.iland.core.web.rest.api.VdcResource;
import com.iland.core.web.rest.api.VmResource;
import com.iland.core.web.sdk.Client;
import com.iland.core.web.sdk.connection.IlandApiException;
import com.iland.core.web.sdk.connection.OAuthException;

/**
 * Example Java app for iland java sdk.
 * 
 */
public class App {

  private static String catalogUuid = "";

  private static String vdcUuid = "";

  private static String vappTemplate = "";

  private static String networkUuid = "";
  
  private static String ERROR = "error";

  private static final Client apiClient;

  private static final VmResource vmResource;

  private static final VcdCatalogVappTemplateUpload vappTemplateUpload;

  private static final TaskResource taskResource;

  private static final VappResource vappResource;

  private static final UserResource userResource;
  
  private static final VdcResource vdcResource;
  
  private static final VappTemplateResource vappTemplateResource;
  
  private static final CatalogResource catalogResource;
  
  private static final Property property;

  private static final DateTimeFormatter formatter =
      DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneOffset.UTC);
  
  static {
    property = new Property();
    apiClient = getApiClient();
    vmResource = apiClient.getVmResource();
    vappTemplateUpload = apiClient.getVcdCatalogVappTemplateUploadV1();
    taskResource = apiClient.getTaskResource();
    vappResource = apiClient.getVappResource();
    userResource = apiClient.getUserResource();
    vdcResource = apiClient.getVdcResource();
    vappTemplateResource = apiClient.getVappTemplateResource();
    catalogResource = apiClient.getCatalogResource();
  }

  /**
   * Logs into account provided in the app.properties file.
   * 
   * Then we get the user's inventory and print all entities the user has
   * permission for. We also get UUIDs used in vApp creation next. 
   * 
   * Then we create a vApp and return it's vApp UUID.
   * 
   * With the newly create vApp, we get it's VM and perform various operations on it.
   *
   * Finally we download the vApp so we can upload as an OVA.
   * 
   * @param args
   */
  public static void main(String[] args) {
    login();
    final User user = userResource.getUser(property.getUsername());
    getUsersInventoryAndPrintAllEntities(user);
    if (!catalogUuid.isEmpty() && !vdcUuid.isEmpty() && !vappTemplate.isEmpty()
        && !networkUuid.isEmpty()) {
      final String vappUuid = createVapp();
      final String vmUuid = preformExampleVmOperations(vappUuid);
      downloadVappAndUploadOva(vappUuid, vmUuid);
    } else {
      System.out.println(
          "Could not get required UUIDs. Skipping everything and logging out.");
    }
    logout();
  }

  /**
   * When given a {@link User} user, we get all the entities they have
   * permissions for and print out every one.
   * 
   * This function also sets vdcUuid, networkUuid, vappTemplateUuid and
   * catalogUuid by finding the first instance of each one.
   * 
   * These variables will be used when creating the vApp. 
   * 
   * @param user {@link User} user
   */
  private static void getUsersInventoryAndPrintAllEntities(final User user) {
    final List<OrgEntityTree> userInventory =
        userResource.getUserInventory(user.getName(), user.getCrm());
    for (final OrgEntityTree orgEntityTree : userInventory) {
      if (orgEntityTree.getTree().getType().equals(EntityType.ORG)) {
        final Map<EntityType, List<EntityTreeNode>> childrenNodes =
            orgEntityTree.getTree().getChildren();
        if (!childrenNodes.isEmpty()) {
          for (final EntityType et : childrenNodes.keySet()) {
            transverseEntityTreeNode(childrenNodes.get(et));
          }
          if (vdcUuid.isEmpty()) {
            final List<EntityTreeNode> vdcTree= childrenNodes.get(EntityType.VDC);
            if (vdcTree != null
                && !vdcTree.isEmpty()) {
              final EntityTreeNode vdc = vdcTree.get(0);
              vdcUuid = vdc.getUuid();
              if (vdc.getChildren() != null
                  && vdc.getChildren().get(EntityType.VAPP) != null
                  && !vdc.getChildren().get(EntityType.VAPP).isEmpty()
                  && vdc.getChildren().get(EntityType.VAPP).get(0)
                      .getChildren() != null
                  && vdc.getChildren().get(EntityType.VAPP).get(0).getChildren()
                      .get(EntityType.VAPP_NETWORK) != null
                  && !vdc.getChildren().get(EntityType.VAPP).get(0).getChildren()
                      .get(EntityType.VAPP_NETWORK).isEmpty()) {
                networkUuid = childrenNodes.get(EntityType.VDC).get(0)
                    .getChildren().get(EntityType.VAPP).get(0).getChildren()
                    .get(EntityType.VAPP_NETWORK).get(0).getUuid();
              }
            }
          }
          if (catalogUuid.isEmpty()) {
            final List<EntityTreeNode> catalogTree =
                childrenNodes.get(EntityType.CATALOG);
            if (catalogTree != null && !catalogTree.isEmpty()) {
              catalogUuid = catalogTree.get(0).getUuid();
              if (catalogTree.get(0).getChildren() != null
                  && catalogTree.get(0).getChildren()
                      .get(EntityType.VAPP_TEMPLATE) != null
                  && !catalogTree.get(0).getChildren()
                      .get(EntityType.VAPP_TEMPLATE).isEmpty()) {
                vappTemplate =
                    childrenNodes.get(EntityType.CATALOG).get(0).getChildren()
                        .get(EntityType.VAPP_TEMPLATE).get(0).getUuid();
              }
            }
          }
        } else {
          System.out.println(String.format("No entities found for org=%s.",
              orgEntityTree.getOrgUuid()));
        }
      }
    }
  }

  /**
   * This function creates a vApp based on the entities we found from
   * transversing the user's inventory in the previous method.
   * 
   * We first have to find a valid VM template based on our vApp template we
   * got. So we get the VMs associated with the vApp template and grab the first
   * one for our vApp creation.
   * 
   * To create a vApp we have to pass a vDC UUID and {@link VappSpec} vApp spec
   * to the sdk.
   * 
   * To create a VM within the vApp we must use a {@link VmSpec} VM spec to
   * create it. Also if we want the vApp to be connected to a network we must
   * use a {@link VappNetworkInitializationParams}.
   * 
   * After creation we get the vApps from the vDC where we made it to confirm it
   * was made and return the vApp UUID related to it.
   * 
   * @return {@link String} created vApp's UUID
   */
  private static String createVapp() {
    final List<VappTemplateVm> vappTemplateVms =
        vappTemplateResource.getVappTemplateVms(vappTemplate);
    final String vmUuid = vappTemplateVms.get(0).getUuid();
    final String vappName =
        "Test vApp from Java SDK - " + formatter.format(Instant.now());
    final List<VmSpec> specs =
        Collections.singletonList(new VmSpec("VM name", "VM description",
            IpAddressingMode.POOL, networkUuid, null, vmUuid, "", null));
    final List<IpRange> ipRanges = new ArrayList<>();
    final IpRange ipRange = new IpRange("192.168.2.100", "192.168.2.149");
    final IpRange ipRange2 = new IpRange("192.168.2.151", "192.168.2.160");
    ipRanges.add(ipRange);
    ipRanges.add(ipRange2);
    final VappNetworkInitializationParams vappNetwork =
        new VappNetworkInitializationParams(
            "Test vApp Network from Java SDK - "
                + formatter.format(Instant.now()),
            "test net description", true, false, false, null, "192.168.2.1",
            "255.255.255.0", null, null, "dns-suffix", ipRanges);
    final VappSpec vappSpec = new VappSpec(vappTemplate, vappName,
        "description", FenceMode.ISOLATED, specs, vappNetwork);
    final CoreTask coreTask = vdcResource.addVapp(vdcUuid, vappSpec);
    waitForSyncedTask(coreTask.getUuid(), coreTask.getLocationId());
    final List<Vapp> vapps = vdcResource.getVappsForVdc(vdcUuid);
    Vapp createdVapp = null;
    for (final Vapp vapp : vapps) {
      if (vapp.getName().equals(vappName)) {
        createdVapp = vapp;
        break;
      }
    }
    return createdVapp.getUuid();
  }

  /**
   * In this function, we start, stop and delete a VM.
   *
   * We get the VMs associated with our newly created vApp and get the only VM.
   * 
   * Then we power on the VM and wait for the core task returned to us to sync.
   * Letting the core task sync is important since are doing multiple power
   * operations in a row.
   * 
   * Then we power off the VM and wait for that task to sync.
   * 
   * 
   * @param vappUuid {@link String} vapp UUID
   */
  private static String preformExampleVmOperations(final String vappUuid) {
    final List<Vm> vms = vappResource.getVmsForVapp(vappUuid);
    final Vm vm = vms.get(0);
    final CoreTask startVm = vmResource.powerOnVm(vm.getUuid(), false);
    waitForSyncedTask(startVm.getUuid(), startVm.getLocationId());
    final CoreTask syncedStartVmTask =
        getTask(startVm.getUuid(), startVm.getLocationId());
    if (syncedStartVmTask.getStatus().equals(ERROR)) {
      System.out.println(syncedStartVmTask.getMessage());
    }
    final CoreTask stopVm = vmResource.powerOffVm(vm.getUuid());
    waitForSyncedTask(stopVm.getUuid(), stopVm.getLocationId());
    final CoreTask syncedStopVmTask =
        getTask(stopVm.getUuid(), stopVm.getLocationId());
    if (syncedStopVmTask.getStatus().equals(ERROR)) {
      System.out.println(syncedStopVmTask.getMessage());
    }
    return vm.getUuid();
  }

  /**
   * Here we download a vApp so we can upload it as an OVA.
   * 
   * First we must power off the vApp to download it.
   * 
   * Then we enable it to be downloaded and download it.
   * 
   * To upload the vApp as a vApp template we must chunk the vApp into less than
   * 10MB pieces.
   * 
   * After uploading the vApp template, we delete the VM, vApp, and the vApp template.
   *
   * @param vappUuid {@link String} vapp UUID to be downloaded
   */
  private static void downloadVappAndUploadOva(final String vappUuid, final String vmUuid) {
    try {
      final CoreTask stopVapp = vappResource.powerOffVapp(vappUuid);
      waitForSyncedTask(stopVapp.getUuid(), stopVapp.getLocationId());
    } catch (final IlandApiException e) {
      e.printStackTrace();
    }
    try {
      final CoreTask enableVappDownload = vappResource.enableVappDownload(vappUuid);
      waitForSyncedTask(enableVappDownload.getUuid(),
          enableVappDownload.getLocationId());
    } catch (final IlandApiException e) {
      e.printStackTrace();
    }
    InputStream is = vappResource.download(vappUuid);
    final String vappTemplateName = "vApp template from Java SDK Example - "
        + formatter.format(Instant.now());
    try {
      final byte[] file = org.apache.commons.io.IOUtils.toByteArray(is);
      is = new ByteArrayInputStream(file);
      try {
        // We need to chunk the file into chunks of less than 10MB otherwise the
        // request will be rejected 1 MB chunks
        final int CHUNK_SIZE = 1024 * 1024;
        final int totalChunks = (file.length / CHUNK_SIZE) + 1;
        byte[] buffer = new byte[CHUNK_SIZE];
        int read;
        int count = 1;
        while ((read = is.read(buffer, 0, CHUNK_SIZE)) != -1) {
          // upload current chunk
          final CatalogUploadVappTemplateSpec spec =
              new CatalogUploadVappTemplateSpec();
          spec.setName(vappTemplateName);
          spec.setDescription("test upload");
          spec.setFile(buffer);
          if (read < CHUNK_SIZE) {
            // need to resize the buffer to be exact length since the rest
            // endpoint writes whole byte array and we don't want to write old
            // date in end of array from previous loop iteration
            spec.setFile(Arrays.copyOf(buffer, read));
          }
          spec.setResumableChunkNumber(count);
          spec.setResumableChunkSize(read);
          spec.setResumableIdentifier(vappTemplateName);
          spec.setResumableTotalSize(file.length);
          spec.setTotalChunks(totalChunks);
          final String s =
              vappTemplateUpload.uploadVappTemplate(catalogUuid, spec);
          if (!s.equals("")) {
            // means all chunks are uploaded and we have a core task
            final JsonParser jsonParser = new JsonParser();
            final JsonObject coreTask = jsonParser.parse(s).getAsJsonObject();
            final CoreTask uploadVappTemplate =
                getTask(coreTask.get("uuid").getAsString(),
                    coreTask.get("location_id").getAsString());
            waitForSyncedTask(uploadVappTemplate.getUuid(),
                uploadVappTemplate.getLocationId());
            break;
          }
          // otherwise read next chunk for processing
          count++;
        }
      } catch (final IOException | IlandApiException e) {
        e.printStackTrace();
      }
    } catch (final IOException e) {
      e.printStackTrace();
    }
    final CoreTask deleteVm = vmResource.deleteVm(vmUuid);
    waitForSyncedTask(deleteVm.getUuid(), deleteVm.getLocationId());
    final CoreTask deleteVapp = vappResource.deleteVapp(vappUuid);
    waitForSyncedTask(deleteVapp.getUuid(), deleteVapp.getLocationId());
    final List<VappTemplate> vappTemplates =
        catalogResource.getVappTemplates(catalogUuid);
    String vappTemplateUuid = "";
    for (final VappTemplate vt : vappTemplates) {
      if (vt.getName().equals(vappTemplateName)) {
        vappTemplateUuid = vt.getUuid();
      }
    }
    final CoreTask deleteVappTemplate =
        vappTemplateResource.deleteVappTemplate(vappTemplateUuid);
    waitForSyncedTask(deleteVappTemplate.getUuid(),
        deleteVappTemplate.getLocationId());
  }


  /**
   * Transverses's a list of entity tree nodes recursively and print's out every
   * entity.
   * 
   * @param entityTreeNode {@link List} of {@link EntityTreeNode} nodes
   */
  private static void transverseEntityTreeNode(
      final List<EntityTreeNode> entityTreeNode) {
    for (final EntityTreeNode node : entityTreeNode) {
      System.out.println(String.format("%s: %s, %s", node.getType().name(),
          node.getName(), node.getUuid()));
      if (node.getChildren() == null || node.getChildren().isEmpty()) {
      } else {
        for (final EntityType et : node.getChildren().keySet()) {
          transverseEntityTreeNode(node.getChildren().get(et));
        }
      }
    }
  }

  /**
   * Gets a Client.
   *
   * @return {@link Client} api client
   */
  private static Client getApiClient() {
    final Client client =
        new Client(property.getClientId(), property.getClientSecret());
    return client;
  }

  /**
   * Login into the Client.
   */
  private static void login() {
    try {
      apiClient.login(property.getUsername(), property.getPassword());
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

  /**
   * Get a core task given it's UUID.
   * 
   * @param taskUuid {@link String} task UUID
   * @param locationId {@link String} location id
   * @return {@link CoreTask} core task
   */
  private static CoreTask getTask(final String taskUuid,
      final String locationId) {
    return taskResource.getTask(locationId, taskUuid);
  }

  /**
   * Given a core task UUID, wait until the core task is synced.
   * 
   * @param taskUuid {@link String} task UUID
   * @param locationId {@link String} location id
   */
  private static void waitForSyncedTask(final String taskUuid,
      final String locationId) {
    boolean synced = false;
    CoreTask coreTask;
    while (!synced) {
      try {
        Thread.sleep(2000);
      } catch (final InterruptedException e) {
        System.out
            .println(String.format("Error while waiting for synced task=%s. %s",
                taskUuid, e.getMessage()));
      }
      coreTask = getTask(taskUuid, locationId);
      synced = coreTask.isSynced();
    }
  }

}
