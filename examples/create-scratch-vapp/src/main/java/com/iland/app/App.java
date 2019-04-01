package com.iland.app;

import java.util.Collections;
import java.util.List;

import com.iland.core.api.iam.IamEntityType;
import com.iland.core.web.rest.api.TaskResource;
import com.iland.core.web.rest.api.UserResource;
import com.iland.core.web.rest.api.VappResource;
import com.iland.core.web.rest.api.VdcResource;
import com.iland.core.web.rest.api.VmResource;
import com.iland.core.web.rest.request.vapp.BuildVappRequest;
import com.iland.core.web.rest.request.vm.BuildVmRequest;
import com.iland.core.web.rest.response.iam.UserInventoryEntityResponse;
import com.iland.core.web.rest.response.task.TaskResponse;
import com.iland.core.web.rest.response.user.UserCompanyInventoryResponse;
import com.iland.core.web.rest.response.user.UserInventoryResponse;
import com.iland.core.web.rest.response.vapp.VappListResponse;
import com.iland.core.web.rest.response.vapp.VappResponse;
import com.iland.core.web.rest.response.vm.VmListResponse;
import com.iland.core.web.rest.response.vm.VmResponse;
import com.iland.core.web.sdk.Client;
import com.iland.core.web.sdk.connection.OAuthException;

/**
 * Example Java app that demonstrates basic use case of the iland's Java SDK.
 *
 */
public class App {

  final static String USERNAME = "";
  final static String PASSWORD = "";
  final static String CLIENT_NAME = "";
  final static String CLIENT_SECRET = "";

  static Client apiClient = new Client(CLIENT_NAME, CLIENT_SECRET);

  static UserResource userResource;

  static VdcResource vdcResource;

  static TaskResource taskResource;

  static VappResource vappResource;

  static VmResource vmResource;

  public static void main(String[] args) {
    authentication();
    userResource = apiClient.getUserResource();
    vdcResource = apiClient.getVdcResource();
    taskResource = apiClient.getTaskResource();
    vappResource = apiClient.getVappResource();
    vmResource = apiClient.getVmResource();
    String vdcUuid = printAndGetVdcUuids();
    String vappUuid = buildVappWithVms(vdcUuid);
    preformExampleVmOperations(vappUuid);
    deleteVapp(vappUuid);
    apiClient.logout();
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
   * Print's all the vDC uuids the user has permission to and lazily gets one
   * for vApp creation.
   */
  static String printAndGetVdcUuids() {
    String vdcUuid = "";
    UserInventoryResponse userInventory =
        userResource.getInventory(USERNAME, null);
    for (UserCompanyInventoryResponse companyInventory : userInventory
        .getInventory()) {
      if (companyInventory.getEntities() != null
          && !companyInventory.getEntities().isEmpty()) {
        List<UserInventoryEntityResponse> vDCS =
            companyInventory.getEntities().get(IamEntityType.IAAS_VDC);
        for (UserInventoryEntityResponse vDC : vDCS) {
          if (vdcUuid.isEmpty()) {
            vdcUuid = vDC.getUuid();
          }
          System.out.println(String.format("vDC name: %s, UUID: %s",
              vDC.getName(), vDC.getUuid()));
        }
      }
    }
    return vdcUuid;
  }

  /**
   * Builds a from scratch vApp that has a basic scratch VM inside it.
   *
   * @param vdcUuid
   */
  static String buildVappWithVms(String vdcUuid) {
    String vappName = "Example vApp's Name";
    String vappDescription = "vApp description";
    String vmName = "Example Scratch VM";
    String vmDescription = "VM's description";
    String computerName = "Computer-Name";
    Integer ram = 2000;
    Integer numOfCpus = 4;
    Integer cpuCoresPerSocket = 2;
    Integer hardwareVersion = 11;
    String operatingSystem = "ubuntu64Guest";
    BuildVmRequest scratchVm = new BuildVmRequest(vmName, vmDescription, null,
        null, null, ram, numOfCpus, cpuCoresPerSocket, hardwareVersion,
        operatingSystem, null, null, null, computerName, null, null);
    BuildVappRequest vappRequest = new BuildVappRequest(vappName,
        vappDescription, Collections.singletonList(scratchVm));
    System.out.println("Building from scratch vApp");
    TaskResponse buildVappTask = vdcResource.buildVapp(vdcUuid, vappRequest);
    waitForSyncedTask(buildVappTask.getUuid());
    TaskResponse syncedBuildVappTask =
        taskResource.getTask(buildVappTask.getUuid());
    if (syncedBuildVappTask.getStatus().equals("ERROR")) {
      System.out.println(syncedBuildVappTask.getMessage());
    }
    VappListResponse vapps = vdcResource.getVappsForVdc(vdcUuid);
    String vappUuid = "";
    for (VappResponse vapp : vapps) {
      if (vapp.getName().equals(vappName)) {
        vappUuid = vapp.getUuid();
        break;
      }
    }
    return vappUuid;
  }

  /**
   * In this function, we start, stop a VM.
   *
   * @param vappUuid
   */
  static void preformExampleVmOperations(final String vappUuid) {
    VmListResponse vms = vappResource.getVmsForVapp(vappUuid);
    VmResponse vm = vms.getData().get(0);
    TaskResponse startVm = vmResource.powerOnVm(vm.getUuid(), false);
    waitForSyncedTask(startVm.getUuid());
    TaskResponse stopVm = vmResource.powerOffVm(vm.getUuid());
    waitForSyncedTask(stopVm.getUuid());
  }

  /**
   * Deletes the vApp.
   * 
   * @param vappUuid
   */
  static void deleteVapp(String vappUuid) {
    System.out.println("Deleting scratch vApp");
    VappResource vappResource = apiClient.getVappResource();
    vappResource.deleteVapp(vappUuid);
  }

  /**
   * This function takes task uuid and waits until it is synced. When a task is
   * synced that doesn't mean it has completed successfully just that it has
   * completed.
   * 
   * @param taskUuid
   */
  static void waitForSyncedTask(String taskUuid) {
    TaskResource taskResource = apiClient.getTaskResource();
    boolean synced = false;
    TaskResponse coreTask;
    while (!synced) {
      try {
        // Thread sleep takes time in milliseconds, here we wait 2 seconds
        // before checking to see if the task has synced yet.
        Thread.sleep(2000);
      } catch (final InterruptedException e) {
        System.out
            .println(String.format("Error while waiting for synced task=%s. %s",
                taskUuid, e.getMessage()));
      }
      coreTask = taskResource.getTask(taskUuid);
      synced = coreTask.isSynced();
    }
  }

}


