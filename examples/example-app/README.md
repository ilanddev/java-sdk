# Example of iland Cloud Java SDK
In this example app, we use the Java SDK to perform various operations:
* Login.
* Get and print all the entities for the user.
* Create a vApp and power on and off its VM.
* Download the vApp which we created and upload it as a vApp template.
* Delete the newly created VM, vApp, and vApp template. 
* Logout.

To run this app, you must fill in your username, password, client-id and client-secret in the file app.properties located in the resources folder.

### Build project
```
mvn clean install
```
### Run example app
```
java -jar target/example-app-1.0.jar
```