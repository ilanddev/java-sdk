package com.ilandjavasdk.app;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.iland.core.api.iam.IamEntityType;
import com.iland.core.api.net.IpAddressingMode;
import com.iland.core.api.net.IpRange;
import com.iland.core.api.net.VappNetworkInitializationParams;
import com.iland.core.api.task.CoreTask;
import com.iland.core.api.vcd.FenceMode;
import com.iland.core.api.vcd.User;
import com.iland.core.api.vcd.VappSpec;
import com.iland.core.api.vcd.VmSpec;
import com.iland.core.web.rest.api.TaskResource;
import com.iland.core.web.rest.api.UserResource;
import com.iland.core.web.rest.api.VappResource;
import com.iland.core.web.rest.api.VappTemplateResource;
import com.iland.core.web.rest.api.VdcResource;
import com.iland.core.web.rest.api.VmResource;
import com.iland.core.web.rest.request.vdc.VdcAddVappFromTemplateRequest;
import com.iland.core.web.rest.request.vdc.VdcAddVappFromTemplateRequest.TemplateVmConfig;
import com.iland.core.web.rest.response.iam.UserInventoryEntityResponse;
import com.iland.core.web.rest.response.task.TaskResponse;
import com.iland.core.web.rest.response.user.UserCompanyInventoryResponse;
import com.iland.core.web.rest.response.user.UserInventoryResponse;
import com.iland.core.web.rest.response.user.UserResponse;
import com.iland.core.web.rest.response.vapp.VappListResponse;
import com.iland.core.web.rest.response.vapp.VappResponse;
import com.iland.core.web.rest.response.vapptemplate.VappTemplateVmListResponse;
import com.iland.core.web.rest.response.vm.VmListResponse;
import com.iland.core.web.rest.response.vm.VmResponse;
import com.iland.core.web.sdk.Client;
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

  private static final Client apiClient;

  private static final VmResource vmResource;

  private static final TaskResource taskResource;

  private static final VappResource vappResource;

  private static final UserResource userResource;
  
  private static final VdcResource vdcResource;
  
  private static final VappTemplateResource vappTemplateResource;
  
  private static final Property property;

  private static final DateTimeFormatter formatter =
      DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneOffset.UTC);
  
  static {
    property = new Property();
    apiClient = getApiClient();
    vmResource = apiClient.getVmResource();
    taskResource = apiClient.getTaskResource();
    vappResource = apiClient.getVappResource();
    userResource = apiClient.getUserResource();
    vdcResource = apiClient.getVdcResource();
    vappTemplateResource = apiClient.getVappTemplateResource();
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
    final UserResponse user = userResource.getUser(property.getUsername());
    getUsersInventoryAndPrintAllEntities(user);
    if (!catalogUuid.isEmpty() && !vdcUuid.isEmpty() && !vappTemplate.isEmpty()
        && !networkUuid.isEmpty()) {
      final String vappUuid = createVapp();
      final String vmUuid = preformExampleVmOperations(vappUuid);
      deleteVmAndVapp(vmUuid, vappUuid);
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
  private static void getUsersInventoryAndPrintAllEntities(
      final UserResponse user) {
    final UserInventoryResponse userInventory =
        userResource.getInventory(user.getName(), "000003");
    for (final UserCompanyInventoryResponse companyInventoryResponse : userInventory
        .getInventory()) {
      final Map<IamEntityType, List<UserInventoryEntityResponse>> entities =
          companyInventoryResponse.getEntities();
      for (final IamEntityType entity : entities.keySet()) {
        for (final UserInventoryEntityResponse e : entities.get(entity)) {
          System.out.println(String.format("%s: %s, %s", entity.name(),
              e.getName(), e.getUuid()));
        }
        if (IamEntityType.IAAS_VDC.equals(entity) && vdcUuid.isEmpty()) {
          vdcUuid = entities.get(entity).get(0).getUuid();
        }
        if (IamEntityType.IAAS_VAPP_NETWORK.equals(entity)
            && networkUuid.isEmpty()) {
          networkUuid = entities.get(entity).get(0).getUuid();
        }
        if (IamEntityType.IAAS_CATALOG.equals(entity)
            && catalogUuid.isEmpty()) {
          catalogUuid = entities.get(entity).get(0).getUuid();
        }
        if (IamEntityType.IAAS_VAPP_TEMPLATE.equals(entity)
            && vappTemplate.isEmpty()) {
          vappTemplate = entities.get(entity).get(0).getUuid();
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
    final VappTemplateVmListResponse vappTemplateVms =
        vappTemplateResource.getVappTemplateVms(vappTemplate);
    final String vmUuid = vappTemplateVms.getData().get(0).getUuid();
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
    final TemplateVmConfig templateVmConfig = new TemplateVmConfig(vmUuid,
        "VM name", null, "VM desription", null, null);
    final TaskResponse taskResponse = vdcResource.addVappFromTemplate(vdcUuid,
        new VdcAddVappFromTemplateRequest(vappTemplate, vappName, "description",
            Collections.singletonList(templateVmConfig)));
    waitForSyncedTask(taskResponse.getUuid());
    final VappListResponse vapps = vdcResource.getVapps(vdcUuid);
    VappResponse createdVapp = null;
    for (final VappResponse vapp : vapps.getData()) {
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
    final VmListResponse vms = vappResource.getVms(vappUuid);
    final VmResponse vm = vms.getData().get(0);
    final TaskResponse startVm = vmResource.powerOnVm(vm.getUuid(), false);
    waitForSyncedTask(startVm.getUuid());
    final TaskResponse stopVm = vmResource.powerOffVm(vm.getUuid());
    waitForSyncedTask(stopVm.getUuid());
    return vm.getUuid();
  }

  /**
   * Delete's the VM and vApp that was created earlier.
   *
   * @param vmUuid {@link String} vm uuid
   * @param vappUuid {@link String} vapp uuid
   */
  private void deleteVmAndVapp(final String vmUuid, final String vappUuid) {
    final TaskResponse deleteVm = vmResource.delete(vmUuid);
    waitForSyncedTask(deleteVm.getUuid());
    final TaskResponse deleteVapp = vappResource.deleteVapp(vappUuid);
    waitForSyncedTask(deleteVapp.getUuid());
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
   * @return {@link CoreTask} core task
   */
  private static TaskResponse getTask(final String taskUuid) {
    return taskResource.getTask(taskUuid);
  }

  /**
   * Given a core task UUID, wait until the core task is synced.
   * 
   * @param taskUuid {@link String} task UUID
   */
  private static void waitForSyncedTask(final String taskUuid) {
    boolean synced = false;
    TaskResponse coreTask;
    while (!synced) {
      try {
        Thread.sleep(2000);
      } catch (final InterruptedException e) {
        System.out
            .println(String.format("Error while waiting for synced task=%s. %s",
                taskUuid, e.getMessage()));
      }
      coreTask = getTask(taskUuid);
      synced = coreTask.isSynced();
    }
  }

}
