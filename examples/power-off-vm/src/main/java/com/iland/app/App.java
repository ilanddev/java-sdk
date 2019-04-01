package com.iland.app;

import java.util.List;

import com.iland.core.api.iam.IamEntityType;
import com.iland.core.web.rest.api.TaskResource;
import com.iland.core.web.rest.api.UserResource;
import com.iland.core.web.rest.api.VappResource;
import com.iland.core.web.rest.api.VmResource;
import com.iland.core.web.rest.response.iam.UserInventoryEntityResponse;
import com.iland.core.web.rest.response.task.TaskResponse;
import com.iland.core.web.rest.response.user.UserCompanyInventoryResponse;
import com.iland.core.web.rest.response.user.UserInventoryResponse;
import com.iland.core.web.rest.response.vm.VmListResponse;
import com.iland.core.web.rest.response.vm.VmResponse;
import com.iland.core.web.sdk.Client;
import com.iland.core.web.sdk.connection.OAuthException;

/**
 * Power off and delete a VM.
 *
 */
public class App {

  final static String USERNAME = "";
  final static String PASSWORD = "";
  final static String CLIENT_NAME = "";
  final static String CLIENT_SECRET = "";

  static Client apiClient = new Client(CLIENT_NAME, CLIENT_SECRET);

  static TaskResource taskResource;

  static VappResource vappResource;

  static VmResource vmResource;

  static UserResource userResource;
    
  public static void main(String[] args) {
    authentication();
    taskResource = apiClient.getTaskResource();
    vappResource = apiClient.getVappResource();
    vmResource = apiClient.getVmResource();
    userResource = apiClient.getUserResource();
    String vappUuid = printAndGetVapp();
    String vmUuid = performExampleVmOperations(vappUuid);
    deleteVm(vmUuid);
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
   * Print's all the vApps the user has permission to and get's the uuid of the vApp
   * of the name you pass.
   */
  static String printAndGetVapp() {
    String vappName = "Name of vApp to get";
    String vappUuid = "";
    UserInventoryResponse userInventory =
            userResource.getInventory(USERNAME, null);
    for (UserCompanyInventoryResponse companyInventory : userInventory
            .getInventory()) {
      if (companyInventory.getEntities() != null
              && !companyInventory.getEntities().isEmpty()) {
        List<UserInventoryEntityResponse> vApps =
                companyInventory.getEntities().get(IamEntityType.IAAS_VAPP);
        for (UserInventoryEntityResponse vApp : vApps) {
          if (vApp.getName().equals(vappName)) {
            vappUuid = vApp.getUuid();
          }
          System.out.println(String.format("vApp name: %s, UUID: %s",
                  vApp.getName(), vApp.getUuid()));
        }
      }
    }
    return vappUuid;
  }


  /**
   * In this function, we start, stop a VM for the vApp you pass.
   *
   * @param vappUuid
   */
  static String performExampleVmOperations(final String vappUuid) {
    VappResource vappResource = apiClient.getVappResource();
    VmListResponse vmResponses = vappResource.getVmsForVapp(vappUuid);
    VmResponse vm = vmResponses.getData().get(0);
    TaskResponse startVm = vmResource.powerOnVm(vm.getUuid(), false);
    waitForSyncedTask(startVm.getUuid());
    TaskResponse stopVm = vmResource.powerOffVm(vm.getUuid());
    waitForSyncedTask(stopVm.getUuid());
    return vm.getUuid();
  }

  /**
   * Deletes a VM.
   *
   * @param vmUuid
   */
  static void deleteVm(String vmUuid) {
    System.out.println("Deleting VM");
    VmResource vmResource = apiClient.getVmResource();
    vmResource.deleteVm(vmUuid);
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
